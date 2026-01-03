package com.example.collage

data class RectFNorm(val x: Float, val y: Float, val w: Float, val h: Float)

data class CollageTemplate(
    val id: String,
    val name: String,
    val slots: List<RectFNorm>
)

object CollageTemplates {
    val all: List<CollageTemplate> = listOf(
        CollageTemplate(
            id = "two_vertical",
            name = "2 Vertical",
            slots = listOf(
                RectFNorm(0f, 0f, 0.5f, 1f),
                RectFNorm(0.5f, 0f, 0.5f, 1f)
            )
        ),
        CollageTemplate(
            id = "two_horizontal",
            name = "2 Horizontal",
            slots = listOf(
                RectFNorm(0f, 0f, 1f, 0.5f),
                RectFNorm(0f, 0.5f, 1f, 0.5f)
            )
        ),
        CollageTemplate(
            id = "grid_2x2",
            name = "2 x 2",
            slots = listOf(
                RectFNorm(0f, 0f, 0.5f, 0.5f),
                RectFNorm(0.5f, 0f, 0.5f, 0.5f),
                RectFNorm(0f, 0.5f, 0.5f, 0.5f),
                RectFNorm(0.5f, 0.5f, 0.5f, 0.5f)
            )
        ),
        CollageTemplate(
            id = "one_plus_two",
            name = "1 + 2",
            slots = listOf(
                RectFNorm(0f, 0f, 0.6f, 1f),
                RectFNorm(0.6f, 0f, 0.4f, 0.5f),
                RectFNorm(0.6f, 0.5f, 0.4f, 0.5f)
            )
        )
    )
}
