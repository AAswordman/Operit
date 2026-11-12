package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A database-scoped checkpoint for the external metadata part of the v23 upgrade. */
@Entity(tableName = "pending_subagent_owner_restore")
data class PendingSubagentOwnerRestore(@PrimaryKey val id: Int = 1)
