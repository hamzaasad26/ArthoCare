@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class RecipeContent(val title: String, val ingredients: String, val benefits: String)

private val recipeItems = listOf(
    RecipeContent(
        title = "Fatty Fish (Salmon, Mackerel, Sardines)",
        ingredients = "Omega-3 fatty acids, vitamin D, selenium — grill or bake with olive oil and lemon",
        benefits = "Reduces joint inflammation and morning stiffness"
    ),
    RecipeContent(
        title = "Turmeric Golden Milk",
        ingredients = "1 tsp turmeric, pinch black pepper, ½ tsp ginger, 1 cup warm milk, 1 tsp honey",
        benefits = "Curcumin is a natural anti-inflammatory that targets joint pain pathways"
    ),
    RecipeContent(
        title = "Leafy Greens Bowl",
        ingredients = "Spinach, kale, arugula, 2 tbsp olive oil, lemon juice, handful walnuts",
        benefits = "High in antioxidants that neutralize inflammatory compounds"
    ),
    RecipeContent(
        title = "Berry Smoothie",
        ingredients = "½ cup blueberries, ½ cup strawberries, ¼ cup tart cherries, Greek yogurt, 1 tbsp flaxseed",
        benefits = "Anthocyanins reduce inflammation and oxidative stress in joints"
    ),
    RecipeContent(
        title = "Bone Broth",
        ingredients = "Animal bones, 2 tbsp apple cider vinegar, mixed vegetables, herbs, water — simmer 12 hours",
        benefits = "Collagen and glycine support joint cartilage health and repair"
    ),
    RecipeContent(
        title = "Walnut and Olive Snack",
        ingredients = "Handful walnuts, extra virgin olive oil drizzle, whole grain crackers",
        benefits = "Monounsaturated fats and polyphenols reduce inflammatory markers"
    ),
    RecipeContent(
        title = "Ginger Lemon Tea",
        ingredients = "1 inch fresh ginger sliced, juice of half a lemon, 1 tsp honey, 250ml hot water",
        benefits = "Gingerols inhibit inflammatory pathways similarly to NSAIDs"
    ),
    RecipeContent(
        title = "Lentil Soup",
        ingredients = "1 cup red lentils, 1 tsp turmeric, 1 tsp cumin, 3 garlic cloves, handful spinach, vegetable broth",
        benefits = "High fiber and polyphenols reduce C-reactive protein levels in RA patients"
    )
)

private val RecipeCardBorder = BorderStroke(0.5.dp, Color(0xFF6B4FA0).copy(alpha = 0.3f))
private val RecipeBgClosed = Color(0xFF1E1630)
private val RecipeBgOpen = Color(0xFF2A1F3D)
private val RecipeScreenBg = Color(0xFF0D0B12)

@Composable
fun RecipesScreen(onNavigateBack: () -> Unit) {
    var expandedRecipe by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = RecipeScreenBg,
        topBar = { FeatureTopAppBar(title = "Recipes", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(RecipeScreenBg)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(recipeItems, key = { it.title }) { recipe ->
                RecipeExpandableCard(
                    recipe = recipe,
                    expanded = expandedRecipe == recipe.title,
                    onHeaderClick = {
                        expandedRecipe =
                            if (expandedRecipe == recipe.title) null else recipe.title
                    }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun RecipeExpandableCard(
    recipe: RecipeContent,
    expanded: Boolean,
    onHeaderClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) RecipeBgOpen else RecipeBgClosed
        ),
        border = RecipeCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFCFB3FF),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFCFB3FF)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Ingredients:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recipe.ingredients,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Benefits:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recipe.benefits,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50).copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
