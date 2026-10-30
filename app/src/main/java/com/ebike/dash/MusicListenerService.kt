package com.ebike.dash

import android.service.notification.NotificationListenerService

/**
 * 空实现：Android 规定，只有开启了“通知使用权”的应用才能读取其它 App
 * （如 Apple Music）正在播放的歌曲信息。我们不读取任何通知内容。
 */
class MusicListenerService : NotificationListenerService()
