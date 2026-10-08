package com.example.data.docs

import com.example.domain.model.DocArticle

object DocumentationRepository {

    val categories = listOf(
        "Linux & Kernel",
        "Networking",
        "Cloud Architecture",
        "Cybersecurity",
        "Open Source",
        "Software Engineering"
    )

    private val articles = listOf(
        DocArticle(
            id = "linux-kernel-arch",
            title = "Linux Kernel Architecture: Monolithic Design and Subsystems",
            category = "Linux & Kernel",
            summary = "An exploration of monolithic kernel design, core abstractions, process schedulers, and user-kernel memory boundaries.",
            content = """
# Linux Kernel Architecture

The Linux kernel is a monolithic operating system kernel responsible for mediating access between user-space applications and underlying physical hardware. Despite its monolithic classification, it is highly modular, supporting dynamically loadable kernel modules (LKMs) that can be inserted or removed at runtime without rebooting the system.

## Primary Subsystems

1. **Process Management (sched)**
   The scheduler governs CPU multiplexing. Contemporary Linux kernels utilize the Completely Fair Scheduler (CFS) and Earliest Eligible Virtual Deadline First (EEVDF), maintaining O(log N) red-black trees to assign execution slices based on process priority and nice values.

2. **Memory Management (mm)**
   Linux provides virtual memory abstraction with demand paging. Each process executes within an isolated 64-bit address space. Page tables, Translation Lookaside Buffers (TLBs), and the buddy allocator collaborate with the slab allocator (SLUB) for fine-grained kernel allocations.

3. **Virtual File System (VFS)**
   The VFS abstracts heterogeneous storage backends (ext4, btrfs, tmpfs, procfs) behind standard POSIX system call interfaces: open, read, write, close. Inode, dentry, and superblock abstractions allow uniform traversal regardless of the filesystem driver.

4. **Network Stack (net)**
   A layered implementation of OSI/TCP protocols supporting socket abstractions, routing tables, network packet filtering (eBPF, netfilter/iptables), and driver interfaces.

5. **Inter-Process Communication (IPC)**
   Mechanisms including UNIX domain sockets, message queues, shared memory, and pipe buffers for synchronized cross-process data exchange.
            """.trimIndent(),
            tags = listOf("kernel", "posix", "memory", "scheduler", "vfs"),
            readingTimeMinutes = 6
        ),
        DocArticle(
            id = "tcp-congestion-control",
            title = "TCP Congestion Control: Reno, Cubic, and BBR Analysis",
            category = "Networking",
            summary = "How transport protocols balance throughput and latency in packet-switched networks using loss and delay signals.",
            content = """
# TCP Congestion Control Mechanisms

Transmission Control Protocol (TCP) ensures reliable, in-order byte stream delivery across unreliable networks. Congestion control prevents network saturation by regulating data injection rates.

## Classical Loss-Based: Reno & NewReno
Traditional algorithms treat packet drop (loss) as the primary indicator of network queue saturation:
- **Slow Start**: Exponential window expansion until threshold (ssthresh) is reached.
- **Congestion Avoidance**: Additive Increase, Multiplicative Decrease (AIMD) algorithm. Upon packet loss, window is halved.

## Modern Delay/Buffer-Aware: CUBIC
CUBIC replaces linear additive increase with a cubic function centered on the congestion window achieved before the last congestion event. It delivers aggressive recovery in high-bandwidth-delay product (BDP) networks while ensuring fairness to competing flows.

## Bottleneck Bandwidth and Round-Trip (BBR)
Engineered by Google, BBR models the physical network by measuring:
- Maximum delivery rate (bottleneck bandwidth, BtlBw)
- Minimum round-trip time (RTprop)

BBR controls data rate rather than purely reacting to packet drops, effectively eliminating bufferbloat at intermediate routers.
            """.trimIndent(),
            tags = listOf("tcp", "cubic", "bbr", "congestion", "networking"),
            readingTimeMinutes = 5
        ),
        DocArticle(
            id = "tls-handshake-protocol",
            title = "TLS 1.3 Protocol: 1-RTT Handshake and Forward Secrecy",
            category = "Cybersecurity",
            summary = "Cryptographic foundations of Transport Layer Security 1.3, ephemeral Diffie-Hellman exchanges, and zero round-trip resumption.",
            content = """
# TLS 1.3 Specification Overview

RFC 8446 established TLS 1.3, introducing substantial latency reductions and security simplifications over TLS 1.2.

## Key Cryptographic Improvements
1. **Elimination of Obsolete Primitives**: Deprecated RSA key exchange, CBC-mode ciphers, SHA-1, and arbitrary curve parameters.
2. **Mandatory Authenticated Encryption (AEAD)**: Only authenticated ciphers are permitted:
   - AES-128-GCM
   - AES-256-GCM
   - ChaCha20-Poly1305
3. **Mandatory Ephemeral Forward Secrecy**: All key exchanges rely on Ephemeral Diffie-Hellman (ECDHE or DHE), guaranteeing that compromise of long-term server private keys cannot compromise historical sessions.

## 1-RTT Handshake Flow
In TLS 1.3, the client sends its supported ciphers alongside key share guesses in the initial `ClientHello`. The server responds with `ServerHello` containing its chosen key share and certificate, allowing cryptographic keys to be derived immediately. Application data can be transmitted in just one round-trip (1-RTT).
            """.trimIndent(),
            tags = listOf("tls", "cryptography", "aead", "diffie-hellman", "security"),
            readingTimeMinutes = 5
        ),
        DocArticle(
            id = "container-isolation-cgroups",
            title = "Container Isolation: Linux Namespaces, cgroups, and seccomp",
            category = "Cloud Architecture",
            summary = "Technical examination of operating system level virtualization primitives powering container engines.",
            content = """
# Container Isolation Primitives

Containers are not virtual machines; they are standard user processes constrained by operating system isolation primitives.

## 1. Linux Namespaces (Visibility Control)
Namespaces restrict what a process can see:
- **pid**: Independent process tree numbering. PID 1 inside the container is an isolated child process on the host.
- **net**: Independent network devices, IP routing tables, port bindings, and firewall rules.
- **mnt**: Dedicated filesystem mount point hierarchy.
- **ipc**: System V IPC and POSIX message queues.
- **uts**: Hostname and domain name independence.
- **user**: Root user privileges inside the container mapped to an unprivileged UID outside.

## 2. Control Groups (cgroups v2 - Resource Governance)
While namespaces restrict visibility, cgroups restrict consumption:
- CPU limits and share quotas via CFS scheduler.
- Hard and soft memory caps with Out-Of-Memory (OOM) killer controls.
- Block I/O throughput and IOPS throttles.

## 3. System Call Filtering (seccomp-bpf)
Restricts which system calls a container process can invoke. Standard container profiles filter out risky calls (e.g., kexec_load, reboot, bpf), dramatically shrinking the kernel attack surface.
            """.trimIndent(),
            tags = listOf("containers", "namespaces", "cgroups", "seccomp", "linux"),
            readingTimeMinutes = 6
        ),
        DocArticle(
            id = "raft-consensus-algorithm",
            title = "Raft Consensus Algorithm: Leader Election and Log Replication",
            category = "Cloud Architecture",
            summary = "Understanding distributed consensus, state machine replication, leader leases, and safety invariants in distributed systems.",
            content = """
# The Raft Consensus Algorithm

Raft is a consensus protocol designed for state machine replication across a cluster of distributed nodes, emphasizing formal understandability.

## Fundamental Roles
At any given moment, a node occupies one of three states:
- **Leader**: Accepts client commands, appends entries to its log, and directs replication to followers.
- **Follower**: Passive recipient of Heartbeat and AppendEntries RPCs from the leader.
- **Candidate**: Transitions here upon election timeout to solicit votes from peer nodes.

## Leader Election
If a follower receives no heartbeat within a randomized election timeout (e.g., 150ms-300ms), it increments the current Term, votes for itself, and broadcasts `RequestVote` RPCs. A candidate is elected leader when it secures a strict majority of cluster votes.

## Safety Invariants
- **Election Safety**: At most one leader can be elected per term.
- **Leader Append-Only**: A leader never overwrites or truncates its own log entries; it only appends.
- **Log Matching Property**: If two logs contain an entry with the same index and term, then the logs are identical in all entries through the given index.
            """.trimIndent(),
            tags = listOf("distributed-systems", "raft", "consensus", "replication"),
            readingTimeMinutes = 7
        ),
        DocArticle(
            id = "git-internals-storage",
            title = "Git Internals: Directed Acyclic Graphs and Content-Addressable Storage",
            category = "Open Source",
            summary = "Under the hood of Git: cryptographic object hashes, tree nodes, commits, and packing heuristics.",
            content = """
# Git Content-Addressable Storage

Git is fundamentally a content-addressable object store overlaid with a Directed Acyclic Graph (DAG) representing project version history.

## The Object Database (`.git/objects`)
Every object is keyed by the cryptographic hash of its payload (SHA-1 or SHA-256).

### Four Core Object Types:
1. **Blob**: Raw binary data representing file content. Filenames and permissions are omitted.
2. **Tree**: Analogous to directories. Contains ordered lists of pointer entries: file mode, object type, hash, and filename.
3. **Commit**: Metadata bundle pointing to a top-level Tree object, zero or more parent commit hashes, author/committer identity, timestamp, and message.
4. **Tag**: An annotated reference pointing to a specific commit object with cryptographic signatures.

## Immutability and Deduplication
Because object IDs are derived directly from content hashes, identical files across branches or directories share the exact same blob on disk. Objects are strictly immutable: modifying a single byte generates a completely distinct hash.
            """.trimIndent(),
            tags = listOf("git", "vcs", "dag", "storage", "open-source"),
            readingTimeMinutes = 5
        ),
        DocArticle(
            id = "crypto-aead-gcm",
            title = "Authenticated Encryption with Associated Data (AEAD) & AES-GCM",
            category = "Cybersecurity",
            summary = "Cryptographic integrity guarantees, Galois/Counter Mode mechanics, authentication tags, and nonce-reuse hazards.",
            content = """
# Authenticated Encryption with AES-GCM

Confidentiality without authentication is dangerous. Malleability attacks on unauthenticated stream and block ciphers permit adversaries to modify ciphertext in predictable ways without knowing the key.

## What is AEAD?
Authenticated Encryption with Associated Data (AEAD) simultaneously ensures:
1. **Confidentiality**: The plaintext cannot be read by unauthorized parties.
2. **Integrity / Authenticity**: Any bit modification to ciphertext or associated metadata triggers verification failure.

## AES-GCM Mechanics
Galois/Counter Mode (GCM) combines counter mode (CTR) for symmetric encryption with Galois field GHASH arithmetic for computing an authentication tag.

### Crucial Security Principles:
- **Nonce Uniqueness**: A 96-bit (12-byte) initialization vector (IV/nonce) MUST NEVER be reused with the same encryption key. Reusing an IV destroys the authenticity guarantees of GHASH and compromises key material.
- **Constant-Time Tag Verification**: Authentication tag comparison must occur in constant time to prevent side-channel timing disclosures.
- **Fail-Safe Decryption**: If tag verification fails, decryption routines MUST return an error and discard any partial plaintext buffers immediately.
            """.trimIndent(),
            tags = listOf("cryptography", "aes-gcm", "aead", "integrity", "security"),
            readingTimeMinutes = 6
        ),
        DocArticle(
            id = "zero-trust-principles",
            title = "Zero Trust Architecture: Identity, Least Privilege, and Microsegmentation",
            category = "Cybersecurity",
            summary = "Moving beyond perimeter defenses: continuous authentication, mutual TLS, and dynamic policy enforcement.",
            content = """
# Principles of Zero Trust Architecture

Traditional network security relied on perimeter models: external threats were filtered at the firewall, while internal traffic was implicitly trusted. Modern distributed architectures mandate Zero Trust: "Never Trust, Always Verify."

## Core Tenets (NIST SP 800-207)
1. **All Data Sources and Computing Services are Resources**: No asset is inherently trusted based on physical or network location.
2. **All Communication is Secured Regardless of Network Location**: Mutual TLS (mTLS) and encrypted sessions are mandatory across all internal service meshes.
3. **Access to Individual Resources is Granted on a Per-Session Basis**: Authenticating to the network does not grant broad authorization.
4. **Access is Determined by Dynamic Policy**: Contextual telemetry (device health, geolocation, user posture, behavioral anomalies) factors into continuous authorization.
5. **Continuous Evaluation**: Authentication is not a one-time boundary event; session validity and privilege boundaries are evaluated continuously.
            """.trimIndent(),
            tags = listOf("zero-trust", "security", "architecture", "nist", "identity"),
            readingTimeMinutes = 5
        )
    )

    fun getAllArticles(): List<DocArticle> = articles

    fun getArticleById(id: String): DocArticle? = articles.firstOrNull { it.id == id }

    fun getByCategory(category: String): List<DocArticle> = articles.filter { it.category == category }

    fun search(query: String): List<DocArticle> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val tokens = trimmed.split(" ").filter { it.isNotBlank() }

        return articles.mapNotNull { article ->
            var score = 0
            val titleLower = article.title.lowercase()
            val summaryLower = article.summary.lowercase()
            val categoryLower = article.category.lowercase()
            val tagsLower = article.tags.map { it.lowercase() }

            for (token in tokens) {
                if (titleLower.contains(token)) score += 10
                if (tagsLower.any { it.contains(token) }) score += 6
                if (categoryLower.contains(token)) score += 4
                if (summaryLower.contains(token)) score += 2
            }

            if (score > 0) Pair(article, score) else null
        }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
