package com.example.recoverx.model

// Screens-এর মধ্যে temporary ভাবে recover করার জন্য selected files রাখার জায়গা।
// এটা শুধু in-memory — app বন্ধ হলে হারিয়ে যাবে। Phase 16-এ Room DB দিয়ে
// history persist করার সময় এই approach আরও ভালোভাবে সাজানো হবে।
object RecoverySelectionHolder {
    var selectedFiles: List<ScannedFile> = emptyList()
}