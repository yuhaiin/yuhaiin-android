package io.github.asutorufa.yuhaiin.backup

import androidx.core.content.FileProvider

// Android identifies providers by their class name. Use a distinct component
// so backup URI grants cannot resolve to the existing log/update provider.
class BackupFileProvider : FileProvider()
