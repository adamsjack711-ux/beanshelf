import SwiftUI

/// The leaderboard: beans ranked by your rating, brew methods ranked by the
/// average rating of the cups you logged with them.
struct LeaderboardView: View {
    let beans: [Bean]
    let onBack: () -> Void
    let onOpen: (Bean) -> Void

    private var rankedBeans: [Bean] {
        beans.filter { $0.rating > 0 }.sorted {
            $0.rating != $1.rating ? $0.rating > $1.rating : $0.brews.count > $1.brews.count
        }
    }

    private struct MethodStanding: Identifiable {
        let method: String
        let brewCount: Int
        let avgRating: Double?
        var id: String { method }
    }

    private var rankedMethods: [MethodStanding] {
        Dictionary(grouping: beans.flatMap(\.brews), by: \.method)
            .map { method, brews in
                let rated = brews.filter { $0.rating > 0 }
                return MethodStanding(
                    method: method,
                    brewCount: brews.count,
                    avgRating: rated.isEmpty ? nil : rated.map(\.rating).reduce(0, +) / Double(rated.count)
                )
            }
            .sorted {
                let a = $0.avgRating ?? -1
                let b = $1.avgRating ?? -1
                return a != b ? a > b : $0.brewCount > $1.brewCount
            }
    }

    var body: some View {
        ZStack {
            Palette.roast.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 4) {
                        Button(action: onBack) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(Palette.parchment)
                                .frame(width: 44, height: 44)
                        }
                        .accessibilityLabel("Back")
                        Text("Leaderboard")
                            .font(Type.headlineMedium)
                            .foregroundStyle(Palette.parchment)
                    }
                    .padding(.leading, 8)
                    .padding(.top, 12)

                    Eyebrow(text: "Top beans")
                        .padding(.leading, 24)
                        .padding(.top, 20)
                        .padding(.bottom, 6)
                    if rankedBeans.isEmpty {
                        Text("Rate a bag and it climbs on here.")
                            .font(Type.bodyMedium)
                            .foregroundStyle(Palette.dim)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 8)
                    } else {
                        ForEach(Array(rankedBeans.enumerated()), id: \.element.id) { i, bean in
                            BeanStandingRow(rank: i + 1, bean: bean) { onOpen(bean) }
                        }
                    }

                    Eyebrow(text: "Top brew methods")
                        .padding(.leading, 24)
                        .padding(.top, 28)
                        .padding(.bottom, 6)
                    if rankedMethods.isEmpty {
                        Text("Log some brews to rank your methods.")
                            .font(Type.bodyMedium)
                            .foregroundStyle(Palette.dim)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 8)
                    } else {
                        ForEach(Array(rankedMethods.enumerated()), id: \.element.id) { i, standing in
                            MethodStandingRow(rank: i + 1, standing: standing)
                        }
                    }
                }
                .padding(.bottom, 48)
            }
        }
    }

    private struct RankNumeral: View {
        let rank: Int

        var body: some View {
            // Podium gets crema; the field stays quiet.
            Text("\(rank)")
                .font(.system(size: rank <= 3 ? 22 : 18, weight: .bold, design: .serif))
                .foregroundStyle(rank <= 3 ? Palette.crema : Palette.dim)
                .frame(width: 34, alignment: .leading)
        }
    }

    private struct BeanStandingRow: View {
        let rank: Int
        let bean: Bean
        let onClick: () -> Void

        var body: some View {
            Button(action: onClick) {
                HStack(spacing: 0) {
                    RankNumeral(rank: rank)
                    PhotoImage(file: bean.photoFile, targetWidth: 200) {
                        Image(systemName: "cup.and.saucer.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(Palette.dim)
                    }
                    .frame(width: 44, height: 56)
                    .background(Palette.surfaceHigh)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(bean.name)
                            .font(Type.titleMedium)
                            .foregroundStyle(Palette.parchment)
                            .lineLimit(1)
                        let sub = [bean.roaster, bean.brews.isEmpty ? "" : "\(bean.brews.count) brews"]
                            .filter { !$0.isEmpty }
                            .joined(separator: "  ·  ")
                        if !sub.isEmpty {
                            Text(sub)
                                .font(Type.bodySmall)
                                .foregroundStyle(Palette.dim)
                                .lineLimit(1)
                        }
                    }
                    .padding(.leading, 14)
                    Spacer()
                    RoastStamp(rating: bean.rating, size: 44)
                        .padding(.leading, 10)
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 8)
            }
            .buttonStyle(.plain)
        }
    }

    private struct MethodStandingRow: View {
        let rank: Int
        let standing: MethodStanding

        var body: some View {
            HStack(spacing: 0) {
                RankNumeral(rank: rank)
                VStack(alignment: .leading, spacing: 2) {
                    Text(standing.method)
                        .font(Type.titleMedium)
                        .foregroundStyle(Palette.parchment)
                    Text(standing.brewCount == 1 ? "1 brew" : "\(standing.brewCount) brews")
                        .font(Type.bodySmall)
                        .foregroundStyle(Palette.dim)
                }
                Spacer()
                Text(standing.avgRating.map(formatRating) ?? "—")
                    .font(.system(size: 18, weight: .bold, design: .serif))
                    .foregroundStyle(Palette.stampInk)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 10)
        }
    }
}
