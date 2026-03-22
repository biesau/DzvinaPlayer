package com.maxvale.dzvinaplayer.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.maxvale.dzvinaplayer.ui.navigation.Screen
import com.maxvale.dzvinaplayer.ui.navigation.bottomNavigationItems
import com.maxvale.dzvinaplayer.ui.theme.AccentYellow
import com.maxvale.dzvinaplayer.ui.theme.PrimaryDarkRed
import com.maxvale.dzvinaplayer.ui.theme.SurfaceDark
import com.maxvale.dzvinaplayer.ui.theme.White

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    viewModel.navController = navController

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute?.startsWith("player") != true) {
                BottomNavigationBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.AllFiles.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.AllFiles.route) {
                AllFilesScreen(viewModel = viewModel)
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(viewModel = viewModel)
            }
            composable(Screen.Recent.route) {
                RecentScreen(viewModel = viewModel)
            }
            composable(Screen.About.route) {
                AboutScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(androidx.navigation.navArgument("videoPath") { 
                    type = androidx.navigation.NavType.StringType 
                })
            ) { backStackEntry ->
                val videoPath = backStackEntry.arguments?.getString("videoPath")
                if (videoPath != null) {
                    val playerViewModel: com.maxvale.dzvinaplayer.ui.player.PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    com.maxvale.dzvinaplayer.ui.player.PlayerScreen(
                        videoPath = videoPath,
                        viewModel = playerViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = SurfaceDark,
        tonalElevation = 8.dp
    ) {
        bottomNavigationItems.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title, fontWeight = FontWeight.Medium) },
                selected = currentRoute == screen.route,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentYellow,
                    selectedTextColor = AccentYellow,
                    unselectedIconColor = White.copy(alpha = 0.6f),
                    unselectedTextColor = White.copy(alpha = 0.6f),
                    indicatorColor = PrimaryDarkRed
                ),
                onClick = {
                    navController.navigate(screen.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}
