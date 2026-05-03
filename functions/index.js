const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendChatNotification = functions.database
  .ref("/chats/{chatId}/{messageId}")
  .onCreate(async (snapshot, context) => {
    const message = snapshot.val();
    const chatId = context.params.chatId;

    console.log(`[EVENT] Обработка чата: ${chatId}`);

    const parts = chatId.split("_");
    const residentUid = parts[parts.length - 1];
    const serviceId = parts.map((p) => parseInt(p)).find((n) => !isNaN(n));

    let targetTokens = [];
    let finalTitle = "";

    // 1. ОПРЕДЕЛЯЕМ ПОЛУЧАТЕЛЕЙ И ЗАГОЛОВОК
    if (message.senderUid === residentUid) {
      // --- ПИШЕТ ЖИЛЕЦ (Админу) ---
      // Заголовок для админа: "Адрес | Имя" (например: "Миру 26/20 | Коваль О.")
      const address = message.senderAddress || "";
      const name = message.senderDisplayedName || "Мешканець";
      finalTitle = address ? `${address} | ${name}` : name;

      console.log(`Resident ${residentUid} -> Org ${serviceId}`);

      const adminsSnapshot = await admin.firestore()
        .collection("users")
        .where("osbbId", "==", serviceId)
        .get();

      adminsSnapshot.forEach((doc) => {
        const tokens = doc.data().fcmTokens;
        if (tokens) targetTokens.push(...tokens);
      });
    } else {
      // --- ПИШЕТ АДМИН (Жильцу) ---
      // Заголовок для жильца: Название службы (например: "КП Водоканал")
      finalTitle = message.senderDisplayedName || "Відповідь адміністратора";

      console.log(`Admin -> Resident ${residentUid}`);

      const userDoc = await admin.firestore().collection("users").doc(residentUid).get();
      if (userDoc.exists) {
        targetTokens = userDoc.data().fcmTokens || [];
      }
    }

    if (targetTokens.length === 0) {
      console.log("No tokens found.");
      return null;
    }

    const uniqueTokens = [...new Set(targetTokens)];

    // 2. ФОРМИРУЕМ ПАКЕТ (с фиксом бейджей и данных)
    const multicastMessage = {
      tokens: uniqueTokens,
      notification: {
        title: finalTitle,
        body: message.text || "📷 Фотографія",
      },
      android: {
        priority: "high",
        notification: {
          channelId: "chat_messages", // Должен совпадать с MainActivity
          notificationCount: 1,       // Бейдж для Android 11
          sound: "default",
        },
      },
      data: {
        chatId: chatId, // Для навигации при клике
      },
    };

    try {
      // Используем актуальный метод отправки
      const response = await admin.messaging().sendEachForMulticast(multicastMessage);
      console.log(`Success! Sent ${response.successCount} pushes to ${uniqueTokens.length} devices.`);
    } catch (error) {
      console.error("FCM Error:", error);
    }
    return null;
  });

