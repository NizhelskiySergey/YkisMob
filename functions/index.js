const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendChatNotification = functions.database
  .ref("/chats/{chatId}/{messageId}")
  .onCreate(async (snapshot, context) => {
    const message = snapshot.val();
    const chatId = context.params.chatId;

    console.log(`[EVENT] Новое сообщение в: ${chatId}`);

    const parts = chatId.split("_");
    const residentUid = parts[parts.length - 1]; // UID жильца всегда в конце
    const serviceId = parts.map((p) => parseInt(p)).find((n) => !isNaN(n));

    let recipients = []; // Список объектов {uid, tokens}
    let finalTitle = "";

    // 1. ОПРЕДЕЛЯЕМ ПОЛУЧАТЕЛЕЙ И ЗАГОЛОВОК
    if (message.senderUid === residentUid) {
      // --- ПИШЕТ ЖИЛЕЦ (Админу) ---
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
      // --- ПИШЕТ АДМИН (Жильцу) ---
      finalTitle = message.senderDisplayedName || "Відповідь адміністратора";
      const userDoc = await admin.firestore().collection("users").doc(residentUid).get();
      if (userDoc.exists) {
        recipients.push({ uid: residentUid, tokens: userDoc.data().fcmTokens || [] });
      }
    }

    if (recipients.length === 0) return null;

    // 2. ФИЛЬТРАЦИЯ ПО PRESENCE (Система тишины)
    let targetTokens = [];
    for (const recipient of recipients) {
      const presenceSnapshot = await admin.database()
        .ref(`presence/${chatId}/${recipient.uid}`)
        .get();

      // Если в базе presence/ID_ЧАТА/UID стоит true — пользователь в чате, пуш не нужен
      if (presenceSnapshot.exists() && presenceSnapshot.val() === true) {
        console.log(`[PRESENCE] UID ${recipient.uid} сейчас в чате. Пуш отменен.`);
      } else {
        targetTokens.push(...recipient.tokens);
      }
    }

    if (targetTokens.length === 0) {
      console.log("[CANCEL] Все получатели онлайн в чате. Отправка не требуется.");
      return null;
    }

    // Удаляем дубликаты токенов
    const uniqueTokens = [...new Set(targetTokens)];

    // 3. ФОРМИРУЕМ И ОТПРАВЛЯЕМ ПАКЕТ
    const multicastMessage = {
      tokens: uniqueTokens,
      notification: {
        title: finalTitle,
        body: message.text || "📷 Фотографія",
      },
      android: {
        priority: "high",
        notification: {
          channelId: "chat_messages",
          notificationCount: 1,
          sound: "default",
        },
      },
      data: { chatId: chatId },
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(multicastMessage);
      console.log(`[PUSH] Успешно: ${response.successCount}. Пропущено по Presence: ${recipients.length - (response.successCount > 0 ? 1 : 0)}`);
    } catch (error) {
      console.error("FCM Error:", error);
    }
    return null;
  });
