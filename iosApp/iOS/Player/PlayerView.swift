import SwiftUI
import Shared

/// Плеер iPhone/iPad. Другая серия — новый экран плеера (`.id`), а не подмена потока: прогресс
/// пишется в трек, выбранный при старте, как на Android.
struct PlayerView: View {
    let itemId: Int32
    @State private var target: PlayTarget?

    init(itemId: Int32, target: PlayTarget?) {
        self.itemId = itemId
        _target = State(initialValue: target)
    }

    var body: some View {
        PlayerScreen(itemId: itemId, target: target) { next in target = next }
            .id(target)
            .toolbar(.hidden, for: .tabBar)
    }
}

/// Один запуск плеера: своя модель на свой трек.
private struct PlayerScreen: View {
    @StateObject private var viewModel: PlayerViewModel
    let onNext: (PlayTarget) -> Void

    init(itemId: Int32, target: PlayTarget?, onNext: @escaping (PlayTarget) -> Void) {
        _viewModel = StateObject(wrappedValue: PlayerViewModel(itemId: itemId, target: target))
        self.onNext = onNext
    }

    var body: some View {
        PlayerSurface(viewModel: viewModel, onNext: onNext)
            .navigationTitle(viewModel.track.map { track in
                viewModel.item?.isEpisodic == true ? "\(viewModel.item?.title ?? "") · \(track.episodeCode)" : viewModel.item?.title ?? ""
            } ?? "")
            .navigationBarTitleDisplayMode(.inline)
            // Качество и следующая серия — в панели навигации рядом с «назад»: поверх видео они
            // налезали на кнопки AVKit (AirPlay, звук), которые тот рисует в том же углу.
            .toolbar {
                // Название и серия — двумя строками: в одну строку рядом с кнопками номер серии
                // обрезался первым, а он здесь важнее всего.
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text(viewModel.item?.title ?? "").font(.headline).lineLimit(1)
                        if viewModel.item?.isEpisodic == true, let track = viewModel.track {
                            Text(track.episodeCode).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isHeader)
                }
                ToolbarItemGroup(placement: .primaryAction) {
                    if viewModel.qualities.count > 1 { qualityMenu }
                    if let next = viewModel.nextTarget {
                        Button { onNext(next) } label: { Image(systemName: "forward.end.fill") }
                            .accessibilityLabel("Следующая серия")
                    }
                }
            }
    }

    private var qualityMenu: some View {
        Menu {
            ForEach(viewModel.qualities, id: \.self) { quality in
                Button {
                    Task { await viewModel.select(quality: quality) }
                } label: {
                    if quality == viewModel.currentQuality {
                        Label(quality, systemImage: "checkmark")
                    } else {
                        Text(quality)
                    }
                }
            }
        } label: {
            Image(systemName: "slider.horizontal.3")
        }
        .accessibilityLabel("Качество")
    }
}
