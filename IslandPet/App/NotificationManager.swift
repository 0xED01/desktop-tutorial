import Foundation
import UserNotifications

/// 本地通知：宠物快饿了、睡饱了时提醒你
enum NotificationManager {
    private static let enabledKey = "islandpet.notificationsEnabled"

    static var enabled: Bool {
        get { UserDefaults.standard.bool(forKey: enabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: enabledKey) }
    }

    static func requestPermission() async -> Bool {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        enabled = granted
        return granted
    }

    static func reschedule(for state: GameState) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        guard enabled, let pet = state.pet, !pet.isEgg else { return }

        // 饱腹降到 20 时提醒
        let rate = max(pet.hungerRate, 0.1)
        let hoursToHungry = (pet.hunger - 20) / rate
        if hoursToHungry > 0.05 {
            add(id: "hungry", title: "\(pet.name)饿了 🍽️", body: "肚子咕咕叫，快来喂我吃饭吧～", after: hoursToHungry * 3600)
        }

        if pet.isSleeping {
            let hoursToWake = (100 - pet.energy) / 18
            add(id: "wake", title: "\(pet.name)睡饱啦 🌞", body: "精神满满，想和你玩！", after: max(60, hoursToWake * 3600))
        }

        if let wish = state.wish, let item = ShopItem.find(wish.itemID) {
            let remain = wish.expires.timeIntervalSinceNow - 30 * 60
            if remain > 60 {
                add(id: "wish", title: "\(pet.name)的心愿快过期了 💭", body: "它好想吃\(item.emoji)\(item.name)", after: remain)
            }
        }
    }

    private static func add(id: String, title: String, body: String, after seconds: TimeInterval) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, seconds), repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }
}
