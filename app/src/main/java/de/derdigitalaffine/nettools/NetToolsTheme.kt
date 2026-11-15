package de.derdigitalaffine.nettools

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DeepNight=darkColorScheme(
    primary=Color(0xFF68DED6),
    onPrimary=Color(0xFF003735),
    primaryContainer=Color(0xFF164B49),
    onPrimaryContainer=Color(0xFFBAF4EE),
    secondary=Color(0xFFB0C8FF),
    onSecondary=Color(0xFF18335C),
    secondaryContainer=Color(0xFF20364D),
    onSecondaryContainer=Color(0xFFD7E5FF),
    background=Color(0xFF09141E),
    onBackground=Color(0xFFF0F6F9),
    surface=Color(0xFF101E29),
    onSurface=Color(0xFFF0F6F9),
    surfaceContainerLowest=Color(0xFF0A1720),
    surfaceContainerLow=Color(0xFF142430),
    surfaceContainer=Color(0xFF182B38),
    surfaceContainerHigh=Color(0xFF1E3442),
    surfaceVariant=Color(0xFF273B49),
    onSurfaceVariant=Color(0xFFB0C3CD),
    outline=Color(0xFF607989),
    outlineVariant=Color(0xFF354C5B),
    error=Color(0xFFFFB4AF),
    onError=Color(0xFF69000B),
    errorContainer=Color(0xFF8D242D),
    onErrorContainer=Color(0xFFFFDAD6)
)

private val CleanDay=lightColorScheme(
    primary=Color(0xFF006F72),
    onPrimary=Color.White,
    primaryContainer=Color(0xFFC3F2EE),
    onPrimaryContainer=Color(0xFF064F51),
    secondary=Color(0xFF315A8F),
    onSecondary=Color.White,
    secondaryContainer=Color(0xFFD8E7FF),
    onSecondaryContainer=Color(0xFF173A6C),
    background=Color(0xFFF4F7F9),
    onBackground=Color(0xFF172B36),
    surface=Color(0xFFFFFFFF),
    onSurface=Color(0xFF172B36),
    surfaceContainerLowest=Color.White,
    surfaceContainerLow=Color(0xFFFDFEFF),
    surfaceContainer=Color(0xFFEAF1F4),
    surfaceContainerHigh=Color(0xFFE2EBEF),
    surfaceVariant=Color(0xFFD9E5E9),
    onSurfaceVariant=Color(0xFF526670),
    outline=Color(0xFF70848B),
    outlineVariant=Color(0xFFCAD9DE),
    error=Color(0xFFBA1A1A),
    onError=Color.White,
    errorContainer=Color(0xFFFFDAD6),
    onErrorContainer=Color(0xFF410002)
)

private val WorkspaceTypography=Typography(
    headlineSmall=TextStyle(fontSize=25.sp,lineHeight=31.sp,fontWeight=FontWeight.Bold),
    titleLarge=TextStyle(fontSize=21.sp,lineHeight=27.sp,fontWeight=FontWeight.Bold),
    titleMedium=TextStyle(fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold),
    titleSmall=TextStyle(fontSize=15.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontSize=14.sp,lineHeight=19.sp,fontWeight=FontWeight.Medium),
    labelMedium=TextStyle(fontSize=12.sp,lineHeight=17.sp,fontWeight=FontWeight.Medium),
    labelSmall=TextStyle(fontSize=11.sp,lineHeight=15.sp,fontWeight=FontWeight.Medium)
)
private val WorkspaceShapes=Shapes(
    extraSmall=RoundedCornerShape(8.dp),
    small=RoundedCornerShape(12.dp),
    medium=RoundedCornerShape(16.dp),
    large=RoundedCornerShape(22.dp),
    extraLarge=RoundedCornerShape(28.dp)
)

@Composable
internal fun NetToolsTheme(dark:Boolean,content:@Composable ()->Unit) {
    MaterialTheme(
        colorScheme=if(dark) DeepNight else CleanDay,
        typography=WorkspaceTypography,
        shapes=WorkspaceShapes,
        content=content
    )
}
