package me.ibrahim.moviesapp.compose.presentation.main.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.core.FavoriteMoviesRoute
import me.ibrahim.moviesapp.compose.core.MoviesListRoute
import me.ibrahim.moviesapp.compose.core.SearchMoviesRoute
import me.ibrahim.moviesapp.compose.core.SettingsRoute

@Preview(showSystemUi = true)
@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {

    var selectedIndex by remember { mutableIntStateOf(0) }

    val items = listOf("首页", "收藏", "分析", "设置")

    NavigationBar(
        contentColor = Color.White,
        containerColor = colorResource(id = R.color.black3)
    ) {
        items.forEachIndexed { index, label ->
            NavigationBarItem(
                selected = selectedIndex == index,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorResource(id = R.color.green),
                    selectedTextColor = colorResource(id = R.color.green),
                    indicatorColor = colorResource(id = R.color.black3),
                    unselectedIconColor = Color.White,
                    unselectedTextColor = Color.White
                ),
                onClick = {
                    selectedIndex = index
                    when (index) {
                        0 -> navController.navigate(MoviesListRoute) {
                            popUpTo<MoviesListRoute> {
                                inclusive = false
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }

                        1 -> navController.navigate(FavoriteMoviesRoute) {
                            popUpTo<MoviesListRoute> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }

                        2 -> navController.navigate(SearchMoviesRoute) {
                            popUpTo<MoviesListRoute> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }

                        3 -> navController.navigate(SettingsRoute) {
                            popUpTo<MoviesListRoute> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                label = {
                    Text(text = label)
                },
                icon = {
                    when (index) {
                        0 -> Icon(Icons.Filled.Home, contentDescription = null)
                        1 -> Icon(Icons.Filled.Favorite, contentDescription = null)
                        2 -> Icon(Icons.Filled.Analytics, contentDescription = null)
                        3 -> Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                },
            )
        }
    }
}
