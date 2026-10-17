import SwiftUI

struct BagView: View {
    @EnvironmentObject var store: PetStore

    private var state: GameState { store.state }

    private var consumables: [ShopItem] {
        ShopItem.catalog.filter { $0.category.isConsumable && $0.id != ShopItem.freeSnackID && state.count(of: $0.id) > 0 }
    }

    private var accessories: [ShopItem] {
        ShopItem.catalog.filter { $0.category == .accessory && state.ownedAccessories.contains($0.id) }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ThemedBackground()
                List {
                    Section("道具") {
                        if consumables.isEmpty {
                            Text("背包空空的，去商店逛逛吧").foregroundStyle(.secondary)
                        }
                        ForEach(consumables) { item in
                            HStack {
                                Text(item.emoji).font(.title)
                                VStack(alignment: .leading) {
                                    Text(item.name).font(.headline)
                                    Text(item.effectText).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("×\(state.count(of: item.id))").foregroundStyle(.secondary)
                                Button("使用") { store.act { $0.use(item.id) } }
                                    .buttonStyle(.bordered)
                            }
                        }
                    }
                    .listRowBackground(Color.white.opacity(0.06))

                    Section("装扮") {
                        if accessories.isEmpty {
                            Text("还没有装扮，商店里有蝴蝶结、皇冠……").foregroundStyle(.secondary)
                        } else {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(accessories) { item in
                                        let on = state.pet?.accessory == item.id
                                        Button { store.act { $0.equip(item.id) } } label: {
                                            VStack {
                                                Text(item.emoji).font(.system(size: 36))
                                                Text(on ? "已佩戴" : item.name).font(.caption)
                                            }
                                            .padding(10)
                                            .background(RoundedRectangle(cornerRadius: 12).fill(on ? Color.orange.opacity(0.4) : .white.opacity(0.06)))
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    }
                    .listRowBackground(Color.white.opacity(0.06))

                    Section("主题") {
                        ForEach(PetTheme.all, id: \.id) { theme in
                            let owned = state.ownedThemes.contains(theme.id)
                            Button {
                                if owned { store.act { $0.setTheme(theme.id) } }
                            } label: {
                                HStack {
                                    RoundedRectangle(cornerRadius: 8).fill(theme.gradient).frame(width: 44, height: 28)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(theme.accent, lineWidth: 1))
                                    Text(theme.name)
                                    Spacer()
                                    if state.theme == theme.id {
                                        Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                                    } else if !owned {
                                        Text("商店购买").font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .disabled(!owned)
                        }
                    }
                    .listRowBackground(Color.white.opacity(0.06))
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("背包")
        }
    }
}
