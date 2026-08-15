package com.example.recoverx.model

// সর্বশেষ scan-এর আসল ফলাফল এখানে রাখা হবে, Results/Preview screen এখান থেকে পড়বে।
object ScanResultsHolder {
    var results: List<ScannedFile> = emptyList()

    // Scan Complete screen-এ user "View All" নাকি "View Results" চাপলো সেটা এখানে রাখা হয়,
    // ResultsScreen সেটা পড়ে ঠিক করে সব ফাইল দেখাবে নাকি শুধু curated recoverable ফাইল।
    var showAllOnOpen: Boolean = false
}