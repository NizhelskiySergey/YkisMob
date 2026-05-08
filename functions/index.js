const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendChatNotification = functions.database
  .ref("/chats/{chatId}/{messageId}")
  .onCreate(async (snapshot, context) => {
    const message = snapshot.val();
    const chatId = context.params.chatId;
    const senderUid = message.senderUid; // UID того, кто нажал "отправить"

    console.log(`[EVENT] Новое сообщение от ${senderUid} в чате: ${chatId}`);

    const parts = chatId.split("_");
    const residentUid = parts[parts.length - 1];
    const serviceId = parts.map((p) => parseInt(p)).find((n) => !isNaN(n));

    let recipients = [];
    let finalTitle = "";

    // 1. ОПРЕДЕЛЯЕМ ПОТЕНЦИАЛЬНЫХ ПОЛУЧАТЕЛЕЙ
    if (senderUid === residentUid) {
      // ПИШЕТ ЖИЛЕЦ -> ПОЛУЧАТЕЛИ АДМИНЫ
      const address = message.senderAddress || "";
      const name = message.senderDisplayedName || "Мешканець";
      finalTitle = address ? `${address} | ${name}` : name;

      const adminsSnapshot = await admin.firestore()
        .collection("users")
        .where("osbbId", "==", serviceId)
        .get();

      adminsSnapshot.forEach((doc) => {
        recipients.push({ uid: doc.id, tokens: doc.data().fcmTokens || [] });
      });
    } else {
      // ПИШЕТ АДМИН -> ПОЛУЧАТЕЛЬ ЖИЛЕЦ
      finalTitle = message.senderDisplayedName || "Відповідь адміністратора";
      const userDoc = await admin.firestore().collection("users").doc(residentUid).get();
      if (userDoc.exists) {
        recipients.push({ uid: residentUid, tokens: userDoc.data().fcmTokens || [] });
      }
    }

    if (recipients.length === 0) return null;

    // 2. ФИЛЬТРАЦИЯ (СЕБЯ И ОНЛАЙН-СТАТУСА)
    let targetTokens = [];

    for (const recipient of recipients) {
      // ШАГ А: Пропускаем, если этот получатель и есть отправитель
      if (recipient.uid === senderUid) {
        console.log(`[SKIP] Отправитель ${recipient.uid} исключен из рассылки.`);
        continue;
      }

      // ШАГ Б: Проверяем Presence (система тишины)
      const presenceSnapshot = await admin.database()
        .ref(`presence/${chatId}/${recipient.uid}`)
        .get();

      if (presenceSnapshot.exists() && presenceSnapshot.val() === true) {
        console.log(`[PRESENCE] Пользователь ${recipient.uid} в чате. Пуш не нужен.`);
      } else {
        // Добавляем токены только тех, кто НЕ в чате и НЕ является отправителем
        if (recipient.tokens && recipient.tokens.length > 0) {
          targetTokens.push(...recipient.tokens);
        }
      }
    }

    if (targetTokens.length === 0) {
      console.log("[CANCEL] Нет токенов для отправки (все онлайн или отправитель был один).");
      return null;
    }

    const uniqueTokens = [...new Set(targetTokens)];

    // 3. ОТПРАВКА
    const multicastMessage = {
      tokens: uniqueTokens,
      notification: {
        title: finalTitle,
        body: message.text || "📷 Фотографія",
      },
      android: {
        priority: "high", // Важно для скорости доставки
        notification: {
          channelId: "chat_messages",
          sound: "default",
        },
      },
      data: {
        chatId: chatId,
      },
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(multicastMessage);
      console.log(`[PUSH] Отправлено: ${response.successCount}. Ошибок: ${response.failureCount}`);
    } catch (error) {
      console.error("FCM Error:", error);
    }
    return null;
  });
