# Security and governance

**The central problem: materialisation breaks source-system ACLs.** A user who cannot see a requirement in Polarion must not be able to infer it from the graph. This is the most serious security risk in the architecture and must be decided before any code is written.

| Option | How it works | Pros | Cons |
| ---------------- | ---------------------------------- | ---------------- | ---------------- |
| Non-sensitive graph | the graph holds only low-criticality metadata; everything else resolves through federation with the user's token | secure, easy to defend | limited analytics on restricted data |
| ACL propagation | permissions are projected as labels and filtered at query time | full analytics | complex, drifts, needs reconciliation |
| Graph per enclave | separate graphs per confidentiality perimeter | strong isolation | duplication and operating cost |

**Recommendation.** Start with option 1. It is the only one defensible in a security audit without additional work, and it defers an expensive decision until real usage data exists.

Further governance requirements:

- Authentication through corporate OIDC, with identity delegation down to the source system on every application read. Service accounts are permitted only for synchronising non-sensitive metadata.
- Mandatory encryption in transit; encryption at rest for graph and lakehouse.
- Immutable audit log, independent of the graph, with retention aligned to sector requirements.
- Data classification per tool and resource type, blocking projection for the highest classes.
- **Export control**: in defence and aerospace, projecting ITAR- or EAR-controlled data into a unified graph may itself constitute a transfer. This must be checked legally before design, not after.
- Right to erasure: a documented procedure to selectively remove projected data, with verification that a rebuild does not reintroduce it.
