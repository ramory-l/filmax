package com.filmax.data.hub

import io.ktor.network.sockets.InetSocketAddress

/**
 * Широковещательные адреса сетей устройства («192.168.1.255»). Вопрос на общий 255.255.255.255
 * доходит не до всякого хаба: macOS 15 не отдаёт его фоновому процессу без разрешения «Локальная
 * сеть», а на адрес своей подсети хаб отвечает. Не узнали адресов — пустой список.
 */
internal expect fun localBroadcasts(): List<String>

/**
 * IPv4 отправителя цифрами. Не `hostname`: на JVM тот спрашивает имя у DNS — лишний поход в сеть, а
 * имя вида «mac-mini.lan» телевизор потом может и не разрешить.
 */
internal expect fun InetSocketAddress.ipv4(): String?
