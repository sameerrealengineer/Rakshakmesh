package org.rakshakmesh.core

/**
 * Delivery states. These are distinct and the UI must never merge them (PRD principle 2).
 * The ACTION_SENDTO build can only reach HANDED_TO_SMS_APP: we cannot know if the user
 * pressed send, so the app never claims SENT or DELIVERED there.
 */
enum class OutboxState {
    SAVED,
    HANDED_TO_SMS_APP,
    SENT,
    DELIVERED,
    FAILED,
    ;

    fun canMoveTo(next: OutboxState): Boolean = next in allowed.getValue(this)

    /** True when the message has not left the phone in any form. */
    val notSentYet: Boolean get() = this == SAVED || this == FAILED

    private companion object {
        val allowed: Map<OutboxState, Set<OutboxState>> = mapOf(
            SAVED to setOf(HANDED_TO_SMS_APP, SENT, FAILED),
            // User may come back and hand it over again (e.g. signal returned).
            HANDED_TO_SMS_APP to setOf(HANDED_TO_SMS_APP, SENT, FAILED),
            SENT to setOf(DELIVERED, FAILED),
            DELIVERED to emptySet(),
            FAILED to setOf(HANDED_TO_SMS_APP, SENT, FAILED),
        )
    }
}

enum class MessageType { SOS, SAFE, AT_SHELTER }
