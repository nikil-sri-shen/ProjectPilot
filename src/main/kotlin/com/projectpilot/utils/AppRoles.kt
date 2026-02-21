package com.projectpilot.utils

object AppRoles {
    const val ORGANIZER = "ORGANIZER"
    const val MENTOR = "MENTOR"
    const val JUDGE = "JUDGE"
    const val PARTICIPANT = "PARTICIPANT"

    val ALL = setOf(ORGANIZER, MENTOR, JUDGE, PARTICIPANT)

    fun isValid(role: String?): Boolean = role != null && role in ALL

    fun matches(userRole: String?, allowedRoles: Collection<String>): Boolean {
        if (!isValid(userRole)) return false
        return userRole in allowedRoles
    }
}
