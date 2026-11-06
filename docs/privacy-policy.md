# Yuhaiin Android privacy policy

Updated: November 6, 2026

## About this policy

This policy covers Yuhaiin Android, the open-source VPN and network routing app maintained by the yuhaiin project contributors. Privacy questions can be submitted through the project issue tracker using the contact button below. Issues are public: do not attach passwords, subscription links or unredacted logs.

## How Yuhaiin uses the VPN

Yuhaiin processes traffic routed through its VPN, including DNS requests, destination addresses and ports, traffic counters and, when enabled, the originating app UID/package name. This is used for routing, connection details and diagnostics.

Traffic and necessary credentials go to the proxy, DNS or mesh services used by your configuration. Their operators can process that data. Encryption depends on your chosen protocol and settings; use trusted, encrypted endpoints.

The VPN runs in the background until disconnected, and can reconnect after boot if you enable auto-connect. You can disconnect at any time. Agreeing here does not enable reading the full installed app list.

## Access to installed apps

Yuhaiin reads installed app names, package names, icons and system-app status to let you choose which apps use or bypass the VPN, including apps without launcher icons.

The list is cached in memory on this device. It is not automatically uploaded to the project maintainers or used for advertising. Your selected package names are saved in settings and are included if you export or back up your configuration.

Choose Agree and continue to load the list. You can use the VPN without opening this chooser, and withdraw consent in About → Privacy policy.

## Settings, diagnostics and sharing

Configuration is stored in app-specific storage and can contain proxy passwords, subscription credentials, routing choices and selected app packages. Local logs and connection/session statistics can contain destinations, IP addresses, app identifiers and traffic totals.

The log export feature shares files only when you choose to export them. Configuration backups can contain credentials; exported files are not encrypted by Yuhaiin. Android backup or device transfer may copy configuration according to your system settings. Consent receipts stay on this device and are excluded from backups.

If you enable LAN connections, other devices on that network may access the dashboard, configuration and connection details. Enable it only on a trusted network. Do not share logs, backups or dashboard access with untrusted parties.

## External services

Proxy, DNS, subscription, rule-list and optional mesh/control services receive the requests and connection information needed for their functions. Connectivity checks contact the configured or built-in test endpoints. These services are operated independently and have their own data practices.

Checking for updates contacts GitHub for release information. Downloading an update retrieves the APK and its checksum; installation uses the Android system installer after your action. GitHub and download infrastructure can receive your IP address and normal HTTP request information.

Yuhaiin does not sell the installed app inventory or use it for advertising. It does not automatically send that inventory or exported diagnostic files to the project maintainers.

## Retention and deletion

Configuration and saved traffic totals remain until replaced or deleted, or until you clear app storage or uninstall. In-memory app metadata is cleared when you withdraw consent or the process exits. Logs and cached files remain until rotated, cleared or removed with app storage.

To delete all local app data, use Android Settings → Apps → Yuhaiin → Storage → Clear storage. Delete exported files and system backups separately. Data already received by external services follows their retention policies; contact their operators for deletion. Yuhaiin Android does not require a project account.

Withdrawing consent disconnects the VPN and stops future app-list access. It keeps your configuration so you can review it; using either feature again requires a new affirmative choice.

Privacy questions: [yuhaiin Android issue tracker](https://github.com/yuhaiin/yuhaiin-android/issues). Issues are public; do not post credentials or unredacted logs.
