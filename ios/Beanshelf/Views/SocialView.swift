import SwiftUI

/// The social side: account (register/sign in against the built-in server),
/// Feed / Discover / Find people tabs, post cards with cheers + comments,
/// profiles with follower lists and a shareable QR invite.
struct SocialView: View {
    let onBack: () -> Void
    var initialProfile: String? = nil
    var startOnProfile: Bool = false

    @State private var account = SocialClient.account

    var body: some View {
        if let account {
            SocialRoot(
                account: account,
                // Profile tab opens straight to your own profile.
                initialProfile: initialProfile ?? (startOnProfile ? account.username : nil),
                onSignOut: {
                    SocialClient.signOut()
                    self.account = nil
                }
            )
        } else {
            AccountForm(onSignedIn: { account = $0 })
        }
    }
}

// ── Root: in-social navigation stack ────────────────────────────────────────

private enum SocialEntry: Hashable {
    case home
    case profileOf(String)
    case people(username: String, followers: Bool)
}

private struct SocialRoot: View {
    let account: SocialClient.Account
    let initialProfile: String?
    let onSignOut: () -> Void

    @State private var stack: [SocialEntry]
    @State private var commentsPost: SocialClient.FeedPost?

    init(account: SocialClient.Account, initialProfile: String?, onSignOut: @escaping () -> Void) {
        self.account = account
        self.initialProfile = initialProfile
        self.onSignOut = onSignOut
        _stack = State(initialValue: [initialProfile.map { .profileOf($0) } ?? .home])
    }

    private var atRoot: Bool { stack.count <= 1 }

    var body: some View {
        Group {
            switch stack.last ?? .home {
            case .home:
                HomeTabs(
                    account: account,
                    onSignOut: onSignOut,
                    onOpenProfile: { stack.append(.profileOf($0)) },
                    onOpenComments: { commentsPost = $0 },
                    onOpenMe: { stack.append(.profileOf(account.username)) }
                )
            case .profileOf(let username):
                ProfileView(
                    account: account,
                    username: username,
                    showBack: !atRoot,
                    onBack: { pop() },
                    onOpenProfile: { stack.append(.profileOf($0)) },
                    onOpenComments: { commentsPost = $0 },
                    onOpenPeople: { followers in stack.append(.people(username: username, followers: followers)) }
                )
            case .people(let username, let followers):
                PeopleList(
                    account: account,
                    username: username,
                    followers: followers,
                    onBack: { pop() },
                    onOpenProfile: { stack.append(.profileOf($0)) }
                )
            }
        }
        .sheet(item: Binding(
            get: { commentsPost.map { CommentsTarget(post: $0) } },
            set: { commentsPost = $0?.post }
        )) { target in
            CommentsSheet(account: account, post: target.post)
                .presentationDetents([.medium, .large])
                .presentationBackground(Palette.surface2)
        }
    }

    private struct CommentsTarget: Identifiable {
        let post: SocialClient.FeedPost
        var id: String { post.id }
    }

    private func pop() {
        if stack.count > 1 { stack.removeLast() }
    }
}

// ── Home with Feed / Discover / Find tabs ───────────────────────────────────

private struct HomeTabs: View {
    let account: SocialClient.Account
    let onSignOut: () -> Void
    let onOpenProfile: (String) -> Void
    let onOpenComments: (SocialClient.FeedPost) -> Void
    let onOpenMe: () -> Void

    @State private var tab = 0 // 0 Feed, 1 Discover, 2 Find people

    var body: some View {
        ZStack {
            Palette.roast.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                // header — no back arrow; the bottom bar owns top-level navigation
                HStack(spacing: 10) {
                    Avatar(username: account.username, size: 34, onClick: onOpenMe)
                    Button(action: onOpenMe) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(account.display)
                                .font(Type.titleMedium)
                                .foregroundStyle(Palette.parchment)
                            Text("@\(account.username) · your profile")
                                .font(Type.bodySmall)
                                .foregroundStyle(Palette.dim)
                        }
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    Button("Sign out", action: onSignOut)
                        .font(Type.labelLarge)
                        .foregroundStyle(Palette.dim)
                }
                .padding(.leading, 20)
                .padding(.trailing, 16)
                .padding(.top, 10)

                // tab strip
                HStack(spacing: 8) {
                    TabChip(label: "Feed", selected: tab == 0) { tab = 0 }
                    TabChip(label: "Discover", selected: tab == 1) { tab = 1 }
                    TabChip(label: "Find people", selected: tab == 2) { tab = 2 }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)

                if tab == 2 {
                    FindPeople(account: account, onOpenProfile: onOpenProfile)
                } else {
                    PostFeed(
                        account: account,
                        discover: tab == 1,
                        onOpenProfile: onOpenProfile,
                        onOpenComments: onOpenComments
                    )
                }
            }
        }
    }
}

private struct TabChip: View {
    let label: String
    let selected: Bool
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            Text(label)
                .font(Type.labelLarge)
                .foregroundStyle(selected ? Palette.onAccent : Palette.dim)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(selected ? Palette.crema : Palette.surface2, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct PostFeed: View {
    let account: SocialClient.Account
    let discover: Bool
    let onOpenProfile: (String) -> Void
    let onOpenComments: (SocialClient.FeedPost) -> Void

    @State private var posts: [SocialClient.FeedPost] = []
    @State private var loading = true
    @State private var error: String?
    @State private var reloadKey = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Eyebrow(text: discover ? "Recent beans, everyone" : "The beans they're having")
                    Spacer()
                    Button {
                        reloadKey += 1
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 15))
                            .foregroundStyle(Palette.crema)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Refresh")
                }
                .padding(.leading, 24)
                .padding(.trailing, 12)
                .padding(.top, 10)

                if loading {
                    Loader()
                } else if let error {
                    ErrorText(message: error)
                } else if posts.isEmpty {
                    Text(discover
                        ? "No beans posted yet. Be the first — share a bean from its page → Post to feed."
                        : "Nothing here yet. Follow people in Discover or Find people, or post a bean.")
                        .font(Type.bodyMedium)
                        .foregroundStyle(Palette.dim)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 8)
                } else {
                    ForEach(posts) { post in
                        PostCard(
                            account: account,
                            post: post,
                            onOpenProfile: onOpenProfile,
                            onOpenComments: onOpenComments
                        )
                    }
                }
            }
            .padding(.bottom, 48)
        }
        .task(id: "\(discover)-\(reloadKey)") {
            loading = true
            error = nil
            do {
                posts = discover
                    ? try await SocialClient.discover(account)
                    : try await SocialClient.feed(account)
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }
}

// ── One post card, with cheers + comments ───────────────────────────────────

struct PostCard: View {
    let account: SocialClient.Account
    let post: SocialClient.FeedPost
    let onOpenProfile: (String) -> Void
    let onOpenComments: (SocialClient.FeedPost) -> Void

    @State private var cheered: Bool
    @State private var cheerCount: Int

    init(account: SocialClient.Account, post: SocialClient.FeedPost,
         onOpenProfile: @escaping (String) -> Void,
         onOpenComments: @escaping (SocialClient.FeedPost) -> Void) {
        self.account = account
        self.post = post
        self.onOpenProfile = onOpenProfile
        self.onOpenComments = onOpenComments
        _cheered = State(initialValue: post.iCheered)
        _cheerCount = State(initialValue: post.cheers)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 14) {
                FeedPhoto(post: post)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 0) {
                        Button("@\(post.username)") { onOpenProfile(post.username) }
                            .font(Type.labelLarge)
                            .foregroundStyle(Palette.crema)
                        Text("  ·  " + relativeTime(post.createdAt))
                            .font(Type.bodySmall)
                            .foregroundStyle(Palette.dim)
                    }
                    Text(post.name)
                        .font(Type.titleMedium)
                        .foregroundStyle(Palette.parchment)
                        .lineLimit(1)
                    let sub = [post.roaster, post.origin].filter { !$0.isEmpty }.joined(separator: "  ·  ")
                    if !sub.isEmpty {
                        Text(sub)
                            .font(Type.bodySmall)
                            .foregroundStyle(Palette.dim)
                            .lineLimit(1)
                    }
                    if !post.notes.isEmpty {
                        Text(post.notes)
                            .font(Type.bodyMedium)
                            .foregroundStyle(Palette.parchment.opacity(0.8))
                            .lineLimit(2)
                            .padding(.top, 2)
                    }
                }
                Spacer()
                if post.rating > 0 {
                    RoastStamp(rating: post.rating, size: 44)
                        .padding(.leading, 8)
                }
            }
            // cheers + comments action row
            HStack(spacing: 24) {
                Button {
                    cheered.toggle()
                    cheerCount += cheered ? 1 : -1
                    let target = cheered
                    Task {
                        do {
                            cheerCount = try await SocialClient.setCheer(account, postId: post.id, on: target)
                        } catch {
                            cheered = !target
                            cheerCount += cheered ? 1 : -1
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: cheered ? "heart.fill" : "heart")
                            .font(.system(size: 15))
                        Text(cheerCount > 0 ? "\(cheerCount)" : "Cheers")
                            .font(Type.bodySmall)
                    }
                    .foregroundStyle(cheered ? Palette.crema : Palette.dim)
                }
                .buttonStyle(.plain)
                Button {
                    onOpenComments(post)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "bubble.right")
                            .font(.system(size: 14))
                        Text(post.commentCount > 0 ? "\(post.commentCount)" : "Comment")
                            .font(Type.bodySmall)
                    }
                    .foregroundStyle(Palette.dim)
                }
                .buttonStyle(.plain)
                Spacer()
            }
            .padding(.top, 8)
            .padding(.leading, 70)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 10)
    }
}

// ── Comments sheet ──────────────────────────────────────────────────────────

private struct CommentsSheet: View {
    let account: SocialClient.Account
    let post: SocialClient.FeedPost

    @State private var comments: [SocialClient.Comment] = []
    @State private var loading = true
    @State private var draft = ""
    @State private var sending = false
    @State private var reload = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Comments on \(post.name)")
                    .font(Type.titleMedium)
                    .foregroundStyle(Palette.parchment)

                if loading {
                    Loader()
                } else if comments.isEmpty {
                    Text("No comments yet. Say something.")
                        .font(Type.bodyMedium)
                        .foregroundStyle(Palette.dim)
                        .padding(.vertical, 12)
                } else {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(comments) { c in
                            (Text("@\(c.username)")
                                .font(Type.labelLarge)
                                .foregroundStyle(Palette.crema)
                            + Text("  \(c.text)")
                                .font(Type.bodyMedium)
                                .foregroundStyle(Palette.parchment))
                                .padding(.vertical, 6)
                        }
                    }
                    .padding(.top, 10)
                }

                HStack(spacing: 8) {
                    TextField("Add a comment…", text: $draft)
                        .outlinedField()
                    Button("Post") {
                        guard !draft.isEmpty, !sending else { return }
                        sending = true
                        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                        Task {
                            if (try? await SocialClient.addComment(account, postId: post.id, text: text)) != nil {
                                draft = ""
                                reload += 1
                            }
                            sending = false
                        }
                    }
                    .font(Type.labelLarge)
                    .foregroundStyle(draft.isEmpty ? Palette.dim : Palette.crema)
                    .disabled(draft.isEmpty || sending)
                }
                .padding(.top, 12)
            }
            .padding(.horizontal, 24)
            .padding(.top, 28)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .task(id: reload) {
            comments = (try? await SocialClient.comments(account, postId: post.id)) ?? []
            loading = false
        }
    }
}

// ── Find people ─────────────────────────────────────────────────────────────

private struct FindPeople: View {
    let account: SocialClient.Account
    let onOpenProfile: (String) -> Void

    @State private var query = ""
    @State private var results: [SocialClient.UserHit] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                TextField("Search by name or @username", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .outlinedField()
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                ForEach($results) { $hit in
                    PersonRow(account: account, hit: $hit, onOpenProfile: onOpenProfile)
                }
                if query.count >= 2 && results.isEmpty {
                    Text("No one found.")
                        .font(Type.bodyMedium)
                        .foregroundStyle(Palette.dim)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 8)
                }
            }
            .padding(.bottom, 48)
        }
        .scrollDismissesKeyboard(.interactively)
        .task(id: query) {
            guard query.count >= 2 else {
                results = []
                return
            }
            try? await Task.sleep(for: .milliseconds(300)) // debounce
            guard !Task.isCancelled else { return }
            if let hits = try? await SocialClient.search(account, query: query) {
                results = hits
            }
        }
    }
}

private struct PersonRow: View {
    let account: SocialClient.Account
    @Binding var hit: SocialClient.UserHit
    let onOpenProfile: (String) -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button {
                onOpenProfile(hit.username)
            } label: {
                HStack(spacing: 12) {
                    Avatar(username: hit.username, size: 40)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(hit.display)
                            .font(Type.titleMedium)
                            .foregroundStyle(Palette.parchment)
                        Text("@\(hit.username)")
                            .font(Type.bodySmall)
                            .foregroundStyle(Palette.dim)
                    }
                }
            }
            .buttonStyle(.plain)
            Spacer()
            Button {
                hit.following.toggle()
                let target = hit.following
                Task {
                    do {
                        try await SocialClient.setFollowing(account, username: hit.username, follow: target)
                    } catch {
                        hit.following = !target
                    }
                }
            } label: {
                Text(hit.following ? "Following" : "Follow")
                    .font(Type.labelLarge)
                    .foregroundStyle(hit.following ? Palette.dim : Palette.crema)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .overlay(Capsule().strokeBorder(Palette.dim.opacity(0.45)))
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 8)
    }
}

// ── Profile (mine shows QR + share; others show follow) ──────────────────────

private struct ProfileView: View {
    let account: SocialClient.Account
    let username: String
    let showBack: Bool
    let onBack: () -> Void
    let onOpenProfile: (String) -> Void
    let onOpenComments: (SocialClient.FeedPost) -> Void
    let onOpenPeople: (Bool) -> Void

    @State private var profile: SocialClient.Profile?
    @State private var posts: [SocialClient.FeedPost] = []
    @State private var following = false

    var body: some View {
        ZStack {
            Palette.roast.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if showBack {
                        Button(action: onBack) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(Palette.parchment)
                                .frame(width: 44, height: 44)
                        }
                        .accessibilityLabel("Back")
                        .padding(.leading, 8)
                        .padding(.top, 10)
                    } else {
                        Spacer().frame(height: 16)
                    }

                    if let p = profile {
                        VStack(spacing: 0) {
                            Avatar(username: p.username, size: 72)
                            Text(p.display)
                                .font(Type.headlineMedium)
                                .foregroundStyle(Palette.parchment)
                                .padding(.top, 10)
                            Text("@\(p.username)")
                                .font(Type.bodyMedium)
                                .foregroundStyle(Palette.dim)
                            HStack(spacing: 28) {
                                Stat(value: "\(p.beans)", label: "beans")
                                Stat(value: "\(p.followers)", label: "followers") { onOpenPeople(true) }
                                Stat(value: "\(p.following)", label: "following") { onOpenPeople(false) }
                            }
                            .padding(.top, 14)

                            if p.isMe {
                                MyProfileShare(profile: p)
                            } else {
                                Button {
                                    following.toggle()
                                    let target = following
                                    Task {
                                        do {
                                            try await SocialClient.setFollowing(account, username: p.username, follow: target)
                                        } catch {
                                            following = !target
                                        }
                                    }
                                } label: {
                                    Text(following ? "Following" : "Follow")
                                        .font(Type.labelLarge)
                                        .foregroundStyle(following ? Palette.dim : Palette.onAccent)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 46)
                                        .background(
                                            following ? Palette.surface2 : Palette.crema,
                                            in: RoundedRectangle(cornerRadius: 12)
                                        )
                                }
                                .padding(.top, 16)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 24)

                        Eyebrow(text: "Their top beans")
                            .padding(.leading, 24)
                            .padding(.top, 24)
                            .padding(.bottom, 4)
                        if posts.isEmpty {
                            Text("No rated beans posted yet.")
                                .font(Type.bodyMedium)
                                .foregroundStyle(Palette.dim)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 8)
                        } else {
                            ForEach(posts) { post in
                                PostCard(
                                    account: account,
                                    post: post,
                                    onOpenProfile: onOpenProfile,
                                    onOpenComments: onOpenComments
                                )
                            }
                        }
                    } else {
                        Loader()
                    }
                }
                .padding(.bottom, 48)
            }
        }
        .task(id: username) {
            if let p = try? await SocialClient.profile(account, username: username) {
                profile = p
                following = p.iFollow
            }
            posts = (try? await SocialClient.userLeaderboard(account, username: username)) ?? []
        }
    }
}

private struct MyProfileShare: View {
    let profile: SocialClient.Profile

    @State private var qr: UIImage?

    var body: some View {
        VStack(spacing: 0) {
            Eyebrow(text: "Follow me on Beanshelf")
            if let qr {
                Image(uiImage: qr)
                    .resizable()
                    .interpolation(.none)
                    .frame(width: 200, height: 200)
                    .padding(12)
                    .background(Color(hex: 0xF0E4D2), in: RoundedRectangle(cornerRadius: 12))
                    .padding(.top, 10)
            }
            Text(profile.profileUrl)
                .font(Type.bodySmall)
                .foregroundStyle(Palette.dim)
                .padding(.top, 8)
            ShareLink(item: "Follow me on Beanshelf: \(profile.profileUrl)") {
                HStack(spacing: 8) {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 15))
                    Text("Share invite link")
                }
                .font(Type.labelLarge)
                .foregroundStyle(Palette.onAccent)
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(Palette.crema, in: RoundedRectangle(cornerRadius: 12))
            }
            .padding(.top, 14)
        }
        .padding(.top, 18)
        .task(id: profile.profileUrl) {
            qr = await Task.detached { Qr.encode(profile.profileUrl) }.value
        }
    }
}

private struct Stat: View {
    let value: String
    let label: String
    var onClick: (() -> Void)? = nil

    var body: some View {
        Button {
            onClick?()
        } label: {
            VStack(spacing: 2) {
                Text(value)
                    .font(.system(size: 22, weight: .bold, design: .serif))
                    .foregroundStyle(Palette.parchment)
                Eyebrow(text: label)
            }
        }
        .buttonStyle(.plain)
        .disabled(onClick == nil)
    }
}

// ── Followers / following list ──────────────────────────────────────────────

private struct PeopleList: View {
    let account: SocialClient.Account
    let username: String
    let followers: Bool
    let onBack: () -> Void
    let onOpenProfile: (String) -> Void

    @State private var people: [SocialClient.UserHit] = []
    @State private var loaded = false

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
                        Text(followers ? "Followers" : "Following")
                            .font(Type.headlineMedium)
                            .foregroundStyle(Palette.parchment)
                    }
                    .padding(.leading, 8)
                    .padding(.top, 10)

                    if !loaded {
                        Loader()
                    } else if people.isEmpty {
                        Text(followers ? "No followers yet." : "Not following anyone yet.")
                            .font(Type.bodyMedium)
                            .foregroundStyle(Palette.dim)
                            .padding(24)
                    } else {
                        ForEach($people) { $hit in
                            PersonRow(account: account, hit: $hit, onOpenProfile: onOpenProfile)
                        }
                    }
                }
                .padding(.bottom, 48)
            }
        }
        .task(id: "\(username)-\(followers)") {
            people = (try? await (followers
                ? SocialClient.followers(account, username: username)
                : SocialClient.followingList(account, username: username))) ?? []
            loaded = true
        }
    }
}

// ── Account form (sign in / register against the built-in server) ───────────

private struct AccountForm: View {
    let onSignedIn: (SocialClient.Account) -> Void

    @State private var username = ""
    @State private var display = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ZStack {
            Palette.roast.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Account")
                        .font(Type.headlineMedium)
                        .foregroundStyle(Palette.parchment)
                        .padding(.top, 12)
                    Text("Follow friends and see the beans they're having. Your shelf stays on your phone — only what you post is shared.")
                        .font(Type.bodyMedium)
                        .foregroundStyle(Palette.dim)
                        .padding(.top, 8)
                        .padding(.bottom, 20)

                    Eyebrow(text: "You").padding(.bottom, 8)
                    TextField("Username — letters, numbers, _", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onChange(of: username) { _, new in username = new.lowercased() }
                        .outlinedField()
                        .padding(.bottom, 10)
                    TextField("Display name (optional)", text: $display)
                        .outlinedField()
                        .padding(.bottom, 10)
                    SecureField("Password", text: $password)
                        .outlinedField()
                        .padding(.bottom, 10)

                    if let error {
                        Text(error)
                            .font(Type.bodyMedium)
                            .foregroundStyle(Palette.danger)
                            .padding(.top, 10)
                    }

                    HStack(spacing: 12) {
                        Button {
                            submit(register: true)
                        } label: {
                            Text("Create account")
                                .font(Type.labelLarge)
                                .foregroundStyle(Palette.onAccent)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(Palette.crema, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .disabled(busy || username.isEmpty || password.count < 4)
                        .opacity(busy || username.isEmpty || password.count < 4 ? 0.5 : 1)
                        Button {
                            submit(register: false)
                        } label: {
                            Text("Sign in")
                                .font(Type.labelLarge)
                                .foregroundStyle(Palette.crema)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Palette.dim.opacity(0.45)))
                        }
                        .disabled(busy || username.isEmpty || password.isEmpty)
                        .opacity(busy || username.isEmpty || password.isEmpty ? 0.5 : 1)
                    }
                    .padding(.top, 20)

                    if busy {
                        ProgressView()
                            .tint(Palette.crema)
                            .padding(.top, 16)
                    }
                }
                .padding(.horizontal, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private func submit(register: Bool) {
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            do {
                let user = username.trimmingCharacters(in: .whitespaces)
                let account = register
                    ? try await SocialClient.register(serverUrl: SocialClient.defaultServer, username: user, display: display, password: password)
                    : try await SocialClient.login(serverUrl: SocialClient.defaultServer, username: user, password: password)
                onSignedIn(account)
            } catch {
                self.error = error.localizedDescription
            }
            busy = false
        }
    }
}

// ── shared bits ─────────────────────────────────────────────────────────────

private struct Avatar: View {
    let username: String
    let size: CGFloat
    var onClick: (() -> Void)? = nil

    var body: some View {
        Button {
            onClick?()
        } label: {
            Text(username.prefix(1).uppercased())
                .font(.system(size: size * 0.4, weight: .bold, design: .serif))
                .foregroundStyle(Palette.onAccent)
                .frame(width: size, height: size)
                .background(Palette.crema, in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(onClick == nil)
    }
}

private struct FeedPhoto: View {
    let post: SocialClient.FeedPost

    @State private var image: UIImage?

    var body: some View {
        Color.clear
            .overlay {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    Image(systemName: "cup.and.saucer.fill")
                        .font(.system(size: 17))
                        .foregroundStyle(Palette.dim)
                }
            }
            .clipped()
            .frame(width: 56, height: 70)
            .background(Palette.surfaceHigh)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .task(id: post.id) {
                image = await SocialClient.fetchPhoto(post: post)
            }
    }
}

private struct Loader: View {
    var body: some View {
        ProgressView()
            .tint(Palette.crema)
            .padding(24)
    }
}

private struct ErrorText: View {
    let message: String

    var body: some View {
        Text(message)
            .font(Type.bodyMedium)
            .foregroundStyle(Palette.danger)
            .padding(.horizontal, 24)
            .padding(.vertical, 8)
    }
}
