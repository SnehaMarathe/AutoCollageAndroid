package com.example.collage

/**
 * Templates are defined in normalized coordinates (0..1).
 * Optimized for Instagram-friendly square canvas (1:1).
 */
object CollageTemplates {

    val one = CollageTemplate(
        id = "one",
        name = "Solo",
        slots = listOf(RectF(0f, 0f, 1f, 1f))
    )

    val twoSplit = CollageTemplate(
        id = "two_split",
        name = "Split 2",
        slots = listOf(
            RectF(0f, 0f, 0.5f, 1f),
            RectF(0.5f, 0f, 0.5f, 1f)
        )
    )

    val twoStack = CollageTemplate(
        id = "two_stack",
        name = "Stack 2",
        slots = listOf(
            RectF(0f, 0f, 1f, 0.5f),
            RectF(0f, 0.5f, 1f, 0.5f)
        )
    )

    val threeColumns = CollageTemplate(
        id = "three_cols",
        name = "3 Columns",
        slots = listOf(
            RectF(0f, 0f, 1f/3f, 1f),
            RectF(1f/3f, 0f, 1f/3f, 1f),
            RectF(2f/3f, 0f, 1f/3f, 1f)
        )
    )

    val threeRows = CollageTemplate(
        id = "three_rows",
        name = "3 Rows",
        slots = listOf(
            RectF(0f, 0f, 1f, 1f/3f),
            RectF(0f, 1f/3f, 1f, 1f/3f),
            RectF(0f, 2f/3f, 1f, 1f/3f)
        )
    )

    val quad = CollageTemplate(
        id = "quad",
        name = "Grid 2×2",
        slots = listOf(
            RectF(0f, 0f, 0.5f, 0.5f),
            RectF(0.5f, 0f, 0.5f, 0.5f),
            RectF(0f, 0.5f, 0.5f, 0.5f),
            RectF(0.5f, 0.5f, 0.5f, 0.5f)
        )
    )

    val heroTwo = CollageTemplate(
        id = "hero_two",
        name = "Hero + 2",
        slots = listOf(
            RectF(0f, 0f, 0.65f, 1f),
            RectF(0.65f, 0f, 0.35f, 0.5f),
            RectF(0.65f, 0.5f, 0.35f, 0.5f)
        )
    )

    val heroThree = CollageTemplate(
        id = "hero_three",
        name = "Hero + 3",
        slots = listOf(
            RectF(0f, 0f, 0.60f, 1f),
            RectF(0.60f, 0f, 0.40f, 1f/3f),
            RectF(0.60f, 1f/3f, 0.40f, 1f/3f),
            RectF(0.60f, 2f/3f, 0.40f, 1f/3f)
        )
    )

    val sixGrid = CollageTemplate(
        id = "six_grid",
        name = "Grid 2×3",
        slots = listOf(
            RectF(0f, 0f, 1f/3f, 0.5f),
            RectF(1f/3f, 0f, 1f/3f, 0.5f),
            RectF(2f/3f, 0f, 1f/3f, 0.5f),
            RectF(0f, 0.5f, 1f/3f, 0.5f),
            RectF(1f/3f, 0.5f, 1f/3f, 0.5f),
            RectF(2f/3f, 0.5f, 1f/3f, 0.5f)
        )
    )

    val mosaic = CollageTemplate(
        id = "mosaic",
        name = "Mosaic",
        slots = listOf(
            RectF(0f, 0f, 0.5f, 0.62f),
            RectF(0.5f, 0f, 0.5f, 0.38f),
            RectF(0.5f, 0.38f, 0.5f, 0.62f),
            RectF(0f, 0.62f, 0.5f, 0.38f),
            RectF(0.5f, 0.62f, 0.5f, 0.38f)
        )
    )

    val all: List<CollageTemplate> = listOf(
        one,
        twoSplit,
        twoStack,
        threeColumns,
        threeRows,
        quad,
        heroTwo,
        heroThree,
        sixGrid,
        mosaic
    )
}
