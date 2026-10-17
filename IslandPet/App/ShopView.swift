import SwiftUI

struct ShopView: View {
    @EnvironmentObject var store: PetStore
    @State private var category: ItemCategory = .food

    private var items: [ShopItem] {
        ShopItem.catalog.filter { $0.category == category && $0.price > 0 }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ThemedBackground()
                ScrollView {
                    VStack(spacing: 14) {
                        Picker("分类", selection: $category) {
                            ForEach(ItemCategory.allCases) { Text($0.name).tag($0) }
                        }
                        .pickerStyle(.segmented)

                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                            ForEach(items) { item in
                                ShopItemCell(item: item)
                            }
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("商店")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { CoinBadge(coins: store.state.coins) } }
        }
    }
}

struct ShopItemCell: View {
    @EnvironmentObject var store: PetStore
    var item: ShopItem

    private var level: Int { store.state.pet?.level ?? 1 }
    private var locked: Bool { item.minLevel > level }
    private var ownedOnce: Bool { !item.category.isConsumable && store.state.owns(item) }
    private var isFavorite: Bool { item.id == store.state.pet?.species.favoriteFood }
    private var isWish: Bool { item.id == store.state.wish?.itemID }

    var body: some View {
        VStack(spacing: 8) {
            ZStack(alignment: .topTrailing) {
                Text(item.emoji).font(.system(size: 44)).frame(maxWidth: .infinity)
                if isWish { tag("心愿", .purple) } else if isFavorite { tag("最爱", .pink) }
            }
            Text(item.name).font(.system(.headline, design: .rounded))
            Text(item.category == .theme ? "灵动岛 / 锁屏 / App 配色" : (item.category == .accessory ? "戴在宠物头上" : item.effectText))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(minHeight: 28)
            if item.category.isConsumable {
                Text("已有 \(store.state.count(of: item.id))").font(.caption2).foregroundStyle(.tertiary)
            }
            Button {
                store.act { $0.buy(item.id) }
            } label: {
                Group {
                    if ownedOnce {
                        Text("已拥有")
                    } else if locked {
                        Text("🔒 Lv.\(item.minLevel)")
                    } else {
                        Text("🪙 \(item.price)")
                    }
                }
                .font(.system(.subheadline, design: .rounded).weight(.bold))
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(ownedOnce || locked ? .gray : .orange)
            .disabled(ownedOnce || locked)
        }
        .padding(12)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func tag(_ text: String, _ color: Color) -> some View {
        Text(text).font(.caption2.bold()).padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.6), in: Capsule())
    }
}
