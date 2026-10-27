package com.v4ex.android.data

/** 仅依据当前令牌已确认的身份授予入口；未知资格默认隐藏。服务端负责最终授权。 */
data class MemberPermissions(
    val username: String = "",
    val canPublish: Boolean = false,
    val canCreateToken: Boolean = false,
) {
    fun canManage(topic: Topic?): Boolean = canPublish && topic != null && topic.member.username == username

    companion object {
        fun resolve(hasToken: Boolean, me: Me?, verified: Boolean): MemberPermissions {
            if (!hasToken || !verified || me == null || me.id <= 0 || me.username.isBlank()) return MemberPermissions()
            if (me.tokenExpiration > 0 && me.tokenExpiration <= System.currentTimeMillis()) return MemberPermissions()
            // 当前 member 数据无可核验的资产余额，boost 的替代资格不作推断。
            return MemberPermissions(me.username, me.pro, me.tokenScope == "everything")
        }
    }
}
