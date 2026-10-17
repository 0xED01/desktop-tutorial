import SwiftUI

struct AdoptView: View {
    @EnvironmentObject var store: PetStore
    @State private var species: Species = .cat
    @State private var name = ""
    @State private var bounce = false

    var body: some View {
        ZStack {
            ThemedBackground()
            ScrollView {
                VStack(spacing: 22) {
                    VStack(spacing: 6) {
                        Text("领养一只灵动岛宠物").font(.system(.title, design: .rounded).weight(.heavy))
                        Text("它会住在你的灵动岛里，随时陪着你").foregroundStyle(.secondary)
                    }
                    .padding(.top, 30)

                    PetAvatarView(species: species, stage: .adult, mood: .happy, size: 150, pose: bounce ? 1 : 0)
                        .animation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true), value: bounce)
                        .onAppear { bounce = true }

                    Text(species.intro)
                        .font(.system(.callout, design: .rounded))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(minHeight: 44)
                        .padding(.horizontal)

                    HStack(spacing: 10) {
                        ForEach(Species.allCases) { sp in
                            Button {
                                withAnimation(.spring) { species = sp }
                            } label: {
                                VStack(spacing: 4) {
                                    PetAvatarView(species: sp, stage: .baby, mood: .normal, size: 48)
                                    Text(sp.name).font(.caption2.weight(.semibold))
                                }
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: 14).fill(.white.opacity(sp == species ? 0.2 : 0.06)))
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(sp == species ? Color.white.opacity(0.6) : .clear, lineWidth: 1.5))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    TextField("给它取个名字", text: $name)
                        .textFieldStyle(.roundedBorder)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 240)
                        .onChange(of: name) { _, v in if v.count > 8 { name = String(v.prefix(8)) } }

                    Button {
                        let finalName = name.trimmingCharacters(in: .whitespaces).isEmpty ? "小\(species.name.prefix(1))" : name
                        store.act { s in
                            s.adopt(name: finalName, species: species)
                            return "领养成功！敲敲蛋壳帮它孵化吧"
                        }
                    } label: {
                        Text("领养这颗蛋 🥚").font(.headline).frame(maxWidth: 240).padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)

                    Text("提示：领养后在主页开启「灵动岛显示」，长按灵动岛就能直接喂食、玩耍、摸摸。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                }
                .padding(.bottom, 40)
            }
        }
    }
}
