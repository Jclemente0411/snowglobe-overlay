# Holiday Plus implementation

The dormant commerce foundation from 0.21.6 has been replaced in 0.22.0 by a real Google Play Billing integration with signed purchase verification, acknowledgement, restoration and enforced feature boundaries.

The agreed product remains **SnowGlobe Holiday Plus**, product ID `holiday_plus_lifetime`, **USD 2.99 one time**, inside `com.snowglobe.overlay`. No separate paid app or subscription has been created.

See [HOLIDAY_PLUS_SETUP.md](HOLIDAY_PLUS_SETUP.md) for implementation details, exact Console fields, the licensing public-key requirement, testing and current activation limitations. See [RELEASE_READINESS_0220.md](RELEASE_READINESS_0220.md) for observed build results.

An empty verification key deliberately leaves checkout disabled. A completed build must not be described as a live paid release until the genuine product/key and Play license-test purchase have been verified.
