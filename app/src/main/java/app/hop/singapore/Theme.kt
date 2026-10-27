package app.hop.singapore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlueLight = lightColorScheme(primary=Color(0xFF3659D9),onPrimary=Color.White,primaryContainer=Color(0xFFE9EEFF),onPrimaryContainer=Color(0xFF2944A3),secondary=Color(0xFF59647B),secondaryContainer=Color(0xFFEFF2F8),onSecondaryContainer=Color(0xFF202A43),tertiary=Color(0xFF80432C),tertiaryContainer=Color(0xFFFFE2D1),onTertiaryContainer=Color(0xFF80432C),background=Color(0xFFF6F7FB),onBackground=Color(0xFF202A43),surface=Color.White,onSurface=Color(0xFF202A43),surfaceVariant=Color(0xFFEFF2F8),onSurfaceVariant=Color(0xFF59647B),outline=Color(0xFF79849B),outlineVariant=Color(0xFFE2E7F1))
private val BlueDark = darkColorScheme(primary=Color(0xFFB3C4FF),onPrimary=Color(0xFF162B6E),primaryContainer=Color(0xFF25365D),onPrimaryContainer=Color(0xFFD3DEFF),secondary=Color(0xFFB4BED5),secondaryContainer=Color(0xFF1D273B),onSecondaryContainer=Color(0xFFE9EEFC),tertiary=Color(0xFFFFD5BF),tertiaryContainer=Color(0xFF553B35),onTertiaryContainer=Color(0xFFFFD5BF),background=Color(0xFF131927),onBackground=Color(0xFFE9EEFC),surface=Color(0xFF222D44),onSurface=Color(0xFFE9EEFC),surfaceVariant=Color(0xFF1D273B),onSurfaceVariant=Color(0xFFB4BED5),outline=Color(0xFF8D9BB9),outlineVariant=Color(0xFF34415C))
@Composable fun HopTheme(mode: String, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=if(mode=="dark" || (mode=="system" && isSystemInDarkTheme())) BlueDark else BlueLight,content=content)
}
