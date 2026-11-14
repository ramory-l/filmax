import SwiftUI
import UIKit
import Shared

// Базовые SwiftUI-компоненты Filmax для iPhone/iPad (аналог `core:designsystem`/`core:ui`).
// Экраны собираются из них, а не из «сырых» View. TV-вариант — в `tvOS/DesignSystem`.

// MARK: - PosterCard

/// Карточка постера (2:3) с названием, опциональным подзаголовком, рейтингом и прогресс-полосой.
/// Универсальна: принимает примитивы, чтобы переиспользоваться для `Item`/`WatchHistory`/`Collection`.
struct PosterCard: View {
    let posterURL: String?
    let title: String
    var subtitle: String? = nil
    /// Доля просмотра 0..1 (для «продолжить смотреть»); `nil` — полоса не показывается.
    var progress: Double? = nil
    /// Готовый текст рейтинга (напр. "7.9"); `nil` — бейдж скрыт.
    var ratingText: String? = nil
    var width: CGFloat = 130

    var body: some View {
        VStack(alignment: .leading, spacing: DS.Spacing.sm) {
            posterImage
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
                .lineLimit(1)
            if let subtitle {
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.onSurfaceVariant)
                    .lineLimit(1)
            }
        }
        .frame(width: width)
    }

    private var posterImage: some View {
        PosterImage(url: posterURL)
            .aspectRatio(posterAspectRatio, contentMode: .fill)
            .frame(width: width, height: width / posterAspectRatio)
            .clipShape(RoundedRectangle(cornerRadius: DS.Radius.poster))
            .overlay(alignment: .topTrailing) {
                if let ratingText {
                    RatingBadge(text: ratingText).padding(DS.Spacing.xs)
                }
            }
            .overlay(alignment: .bottom) {
                if let progress {
                    ProgressBar(fraction: progress)
                        .padding(.horizontal, DS.Spacing.xs)
                        .padding(.bottom, DS.Spacing.xs)
                }
            }
    }
}

private struct ProgressBar: View {
    let fraction: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.black.opacity(0.5))
                Capsule().fill(Theme.accent)
                    .frame(width: geo.size.width * CGFloat(min(max(fraction, 0), 1)))
            }
        }
        .frame(height: 4)
    }
}

// MARK: - RatingBadge

/// Бейдж рейтинга поверх постера.
struct RatingBadge: View {
    let text: String
    var body: some View {
        HStack(spacing: 2) {
            Image(systemName: "star.fill").font(.system(size: 9))
            Text(text).font(.system(size: 11, weight: .bold))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .glassSurface(Capsule())
    }
}

// MARK: - Кнопки

/// Роль кнопки действия: главное — акцентом, остальные — стеклом.
enum FilmaxActionRole {
    case primary, secondary
}

extension View {
    /// Кнопки действий («Смотреть», «Буду смотреть», «В папку»). На iOS 26 — Liquid Glass: главная —
    /// акцентное стекло, остальные — прозрачное; раньше — заливка и контур iOS.
    @ViewBuilder func filmaxActionStyle(_ role: FilmaxActionRole) -> some View {
        if #available(iOS 26, *) {
            switch role {
            case .primary: buttonStyle(.glassProminent).tint(Theme.accent)
            case .secondary: buttonStyle(.glass).foregroundStyle(Theme.text)
            }
        } else {
            switch role {
            case .primary: buttonStyle(.borderedProminent).tint(Theme.accent)
            case .secondary: buttonStyle(.bordered).tint(Theme.text)
            }
        }
    }

    /// Отклик переключателя («Буду смотреть»): иконка перетекает в новую, телефон отвечает лёгким
    /// щелчком. С iOS 17; раньше смена просто мгновенная.
    @ViewBuilder func toggleFeedback(_ isOn: Bool) -> some View {
        if #available(iOS 17, *) {
            contentTransition(.symbolEffect(.replace))
                .sensoryFeedback(.selection, trigger: isOn)
                .animation(Motion.press, value: isOn)
        } else {
            self
        }
    }
}

/// Карточка-ссылка: при нажатии утапливается и пружинит обратно — отклик, как у карточек App Store.
struct PressableCardStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? Self.pressedScale : 1)
            .opacity(configuration.isPressed ? Self.pressedOpacity : 1)
            .animation(Motion.press, value: configuration.isPressed)
    }

    private static let pressedScale: CGFloat = 0.96
    private static let pressedOpacity: Double = 0.85
}

extension ButtonStyle where Self == PressableCardStyle {
    static var pressableCard: PressableCardStyle { PressableCardStyle() }
}

/// Основная/второстепенная кнопка Filmax с акцентом дизайн-системы.
struct FilmaxButton: View {
    enum Style { case primary, secondary }
    let title: String
    var systemImage: String? = nil
    var style: Style = .primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: DS.Spacing.sm) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title).fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
        .filmaxActionStyle(style == .primary ? .primary : .secondary)
        .controlSize(.large)
    }
}

// MARK: - SectionHeader

/// Заголовок секции/рельсы с опциональной кнопкой «Все».
struct SectionHeader: View {
    let title: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
            Spacer()
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.system(size: 14, weight: .semibold))
                    .tint(Theme.accent)
            }
        }
    }
}

// MARK: - Состояния экрана

/// Индикатор загрузки на весь экран.
struct LoadingView: View {
    var body: some View {
        ProgressView()
            .tint(Theme.accent)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Единый компонент ошибки поверх `RequestResult.Error`/`AppError` (§5 доки).
/// Показывает сообщение из общего слоя и кнопку «Повторить».
struct ErrorView: View {
    let message: String
    var systemImage: String = "exclamationmark.triangle.fill"
    var retry: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: DS.Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 44))
                .foregroundColor(Theme.accent)
            Text(message)
                .font(.system(size: 15))
                .foregroundColor(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
            if let retry {
                Button("Повторить", action: retry)
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.accent)
            }
        }
        .padding(DS.Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Пустое состояние (нет данных) — переиспользуется списками/сетками.
struct EmptyStateView: View {
    let message: String
    var systemImage: String = "tray"
    var body: some View {
        VStack(spacing: DS.Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 40))
                .foregroundColor(Theme.onSurfaceVariant)
            Text(message)
                .font(.system(size: 15))
                .foregroundColor(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .padding(DS.Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Ненавязчивый баннер «нет сети» (для офлайн-деградации, перекликается с бэкенд-задачей #42).
struct OfflineBanner: View {
    var body: some View {
        HStack(spacing: DS.Spacing.sm) {
            Image(systemName: "wifi.slash")
            Text("Нет сети — показаны сохранённые данные")
                .font(.system(size: 13, weight: .medium))
        }
        .foregroundColor(.white)
        .padding(.horizontal, DS.Spacing.md)
        .padding(.vertical, DS.Spacing.sm)
        .frame(maxWidth: .infinity)
        .background(Theme.accentSoft)
    }
}

// MARK: - Ряды и сетки тайтлов

/// Горизонтальный ряд тайтлов с заголовком; [onReachEnd] — догрузка, когда показан последний.
struct ItemRail: View {
    let title: String
    let items: [Item]
    var onReachEnd: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: DS.Spacing.sm) {
            SectionHeader(title: title).padding(.horizontal, DS.Spacing.md)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: DS.Spacing.md) {
                    ForEach(items, id: \.id) { item in
                        NavigationLink(value: AppRoute.details(itemId: item.id)) {
                            PosterCard(posterURL: item.posterURL, title: item.title, subtitle: item.posterMeta, ratingText: item.ratingText)
                        }
                        .buttonStyle(.pressableCard)
                        .onAppear { if item.id == items.last?.id { onReachEnd?() } }
                    }
                }
                .padding(.horizontal, DS.Spacing.md)
            }
        }
    }
}

/// Адаптивная сетка постеров: на iPad колонок больше, на iPhone — три.
struct ItemGrid: View {
    let items: [Item]
    var onReachEnd: (() -> Void)? = nil

    private let columns = [GridItem(.adaptive(minimum: 108), spacing: DS.Spacing.md, alignment: .top)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: DS.Spacing.lg) {
            ForEach(items, id: \.id) { item in
                NavigationLink(value: AppRoute.details(itemId: item.id)) {
                    PosterCard(posterURL: item.posterURL, title: item.title, subtitle: item.posterMeta, ratingText: item.ratingText, width: 108)
                }
                .buttonStyle(.pressableCard)
                .onAppear { if item.id == items.last?.id { onReachEnd?() } }
            }
        }
        .padding(.horizontal, DS.Spacing.md)
    }
}

// MARK: - Лента с кадром

/// Лента с кадром под статус-баром — Главная и Детали. Пока лента вверху, панель навигации прозрачна
/// и кадр лежит под часами; стоит прокрутить — панель получает подложку и заголовок. Без этого текст
/// ленты проезжал под часами и кнопкой «назад» без всякого фона.
struct HeroScroll<Content: View>: View {
    private let title: String
    private let underBar: Bool
    private let content: Content
    @State private var scrolled = false

    /// [underBar] — лента начинается кадром и уходит под статус-бар; без кадра ей там делать нечего:
    /// под часами оказался бы заголовок ряда.
    init(title: String, underBar: Bool = true, @ViewBuilder content: () -> Content) {
        self.title = title
        self.underBar = underBar
        self.content = content()
    }

    var body: some View {
        ScrollView {
            content.background(alignment: .top) {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: HeroScrollOffsetKey.self,
                        value: -proxy.frame(in: .named(Self.coordinateSpace)).minY
                    )
                }
            }
        }
        .coordinateSpace(name: Self.coordinateSpace)
        .ignoresSafeArea(edges: underBar ? .top : [])
        .modifier(ScrollOffsetObserver { offset in
            let reached = offset > Self.revealOffset
            if reached != scrolled {
                withAnimation(.easeInOut(duration: 0.25)) { scrolled = reached }
            }
        })
        .navigationTitle(scrolled ? title : "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.regularMaterial, for: .navigationBar)
        .toolbarBackground(scrolled ? .visible : .hidden, for: .navigationBar)
    }

    private static var coordinateSpace: String { "heroScroll" }

    /// Панель проявляется, едва шапка начинает уходить под неё: дальше под часы поехал бы её текст.
    /// Небольшой запас — чтобы панель не мигала от лёгкого касания ленты.
    private static var revealOffset: CGFloat { 24 }
}

private struct HeroScrollOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// Смещение ленты от верха. С iOS 18 — `onScrollGeometryChange`: геометрия содержимого в iOS 26
/// при прокрутке не пересчитывается, и панель так и не проявлялась. До iOS 18 — по ней же, другого
/// способа там нет.
private struct ScrollOffsetObserver: ViewModifier {
    let action: (CGFloat) -> Void

    func body(content: Content) -> some View {
        if #available(iOS 18, *) {
            content.onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top
            } action: { _, offset in
                action(offset)
            }
        } else {
            content.onPreferenceChange(HeroScrollOffsetKey.self, perform: action)
        }
    }
}

/// Кадр шапки во всю ширину (Главная, Детали): снизу уходит в фон, чтобы текст поверх читался на
/// любом кадре, сверху притемнён — шапка лежит под часами статус-бара. Затемнения стоят на месте,
/// а сам кадр двигается с лентой (`HeroMotion`).
struct HeroBackdrop: View {
    let url: String?
    let aspectRatio: CGFloat

    var body: some View {
        PosterImage(url: url)
            .aspectRatio(aspectRatio, contentMode: .fill)
            .frame(maxWidth: .infinity)
            .clipped()
            .modifier(HeroMotion())
            .overlay(LinearGradient(colors: [.clear, Theme.background], startPoint: .center, endPoint: .bottom))
            .overlay(
                LinearGradient(
                    colors: [Theme.background.opacity(0.7), .clear],
                    startPoint: .top,
                    endPoint: UnitPoint(x: 0.5, y: 0.3)
                )
            )
    }
}

/// Движение кадра шапки: потянули ленту вниз — кадр растягивается вверх и не отрывается от края;
/// листают вверх — уезжает медленнее ленты (параллакс), как шапки приложений Apple. С iOS 17
/// (`visualEffect` не трогает раскладку и не пересчитывает ленту на каждом кадре); раньше кадр
/// просто едет с лентой.
private struct HeroMotion: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17, *) {
            content.visualEffect { effect, proxy in
                let top = proxy.frame(in: .scrollView).minY
                let pull = max(top, 0)
                return effect
                    .scaleEffect(1 + pull / max(proxy.size.height, 1), anchor: .bottom)
                    .offset(y: -min(top, 0) * Self.parallax)
            }
        } else {
            content
        }
    }

    /// Доля прокрутки, на которую кадр отстаёт от ленты.
    private static let parallax: CGFloat = 0.4
}

// MARK: - Сегменты

/// Сегменты разделов с шириной по подписи. `Picker(.segmented)` делит ширину поровну, и длинная
/// подпись («Буду смотреть») обрезалась, хотя короткие соседи («Папки») оставляли место. Раздавать
/// ширину по содержимому умеет только `UISegmentedControl` — отсюда обёртка над ним.
struct ContentSizedSegments<Value: Hashable>: UIViewRepresentable {
    let options: [Value]
    let title: (Value) -> String
    @Binding var selection: Value

    func makeUIView(context: Context) -> UISegmentedControl {
        let control = UISegmentedControl()
        control.apportionsSegmentWidthsByContent = true
        control.addAction(UIAction { [coordinator = context.coordinator] action in
            guard let control = action.sender as? UISegmentedControl else { return }
            coordinator.select(index: control.selectedSegmentIndex)
        }, for: .valueChanged)
        return control
    }

    func updateUIView(_ control: UISegmentedControl, context: Context) {
        context.coordinator.parent = self
        let titles = options.map(title)
        let current = (0..<control.numberOfSegments).map { control.titleForSegment(at: $0) ?? "" }
        if current != titles {
            control.removeAllSegments()
            for (index, segmentTitle) in titles.enumerated() {
                control.insertSegment(withTitle: segmentTitle, at: index, animated: false)
            }
        }
        control.selectedSegmentIndex = options.firstIndex(of: selection) ?? UISegmentedControl.noSegment
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    final class Coordinator {
        var parent: ContentSizedSegments

        init(parent: ContentSizedSegments) {
            self.parent = parent
        }

        func select(index: Int) {
            guard parent.options.indices.contains(index) else { return }
            parent.selection = parent.options[index]
        }
    }
}

/// Чип-фильтр: выбранный — акцентом.
struct FilterChip: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .padding(.horizontal, DS.Spacing.md)
                .padding(.vertical, DS.Spacing.sm)
                .foregroundColor(.white)
                .glassSurface(Capsule(), tint: selected ? Theme.accent : nil, interactive: true)
                .animation(Motion.press, value: selected)
        }
        .buttonStyle(.pressableCard)
    }
}
