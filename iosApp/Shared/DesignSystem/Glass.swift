import SwiftUI

// Стекло и движение дизайн-системы — общие для iPhone и Apple TV. На iOS/tvOS 26 стекло системное
// (Liquid Glass): преломляет то, что под ним, и откликается на касание; на старых системах — материал,
// ближайший к нему по виду. Анимации — короткие пружины и растворение, как в приложениях Apple.

extension View {
    /// Стеклянная подложка в форме [shape]. [tint] — окрашенное стекло (выбранный чип);
    /// [interactive] — стекло отзывается на нажатие, у бейджей и плашек отклик не нужен.
    @ViewBuilder func glassSurface<S: Shape>(_ shape: S, tint: Color? = nil, interactive: Bool = false) -> some View {
        if #available(iOS 26, tvOS 26, *) {
            glassEffect(Glass.regular.tint(tint).interactive(interactive), in: shape)
        } else if let tint {
            background(tint, in: shape)
        } else {
            background(.ultraThinMaterial, in: shape)
        }
    }

    /// Появление загруженного экрана: содержимое проявляется вместо индикатора, а не подменяет его
    /// рывком.
    func revealOnLoad(_ loaded: Bool) -> some View {
        animation(Motion.reveal, value: loaded)
    }
}

/// Кривые движения: одни и те же на всех экранах, чтобы приложение двигалось одним характером.
enum Motion {
    /// Отклик на нажатие и выбор — быстрая пружина без «желе».
    static let press = Animation.spring(response: 0.3, dampingFraction: 0.75)
    /// Смена содержимого (сезон, раздел) — мягкое растворение.
    static let swap = Animation.easeInOut(duration: 0.25)
    /// Появление экрана и картинок.
    static let reveal = Animation.easeOut(duration: 0.35)
}
