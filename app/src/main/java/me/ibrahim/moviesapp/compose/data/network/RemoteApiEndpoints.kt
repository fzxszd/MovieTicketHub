package me.ibrahim.moviesapp.compose.data.network

import me.ibrahim.moviesapp.compose.BuildConfig

object RemoteApiEndpoints {
    const val BASE_URL = "https://apis.netstart.cn/maoyan"
    
    // 正在热映
    const val MOVIE_ON_INFO_LIST = "/index/movieOnInfoList"
    // 即将上映
    const val COMING_LIST = "/index/comingList"
    // 电影详情
    const val MOVIE_DETAIL = "/movie/detail"
    // 影院列表 (修正后的路径)
    const val CINEMA_LIST = "/cinemas"

    val AUTH_BASE_URL: String get() = BuildConfig.AUTH_BASE_URL
    const val AUTH_REGISTER = "/auth/register"
    const val AUTH_LOGIN = "/auth/login"
    const val AUTH_ME = "/auth/me"
    const val AUTH_LOGOUT = "/auth/logout"
    const val SYNC_PUSH = "/sync/push"
    const val SYNC_PULL = "/sync/pull"
}
