package at.gregor.layermaxxing

/**
 * Visible part of every island sprite inside its square layout box (Fit, bottom-centred), as fractions
 * (left, top, right, bottom). Generated from the painted WebP alpha (trimmed 3 % of stray pixels) for the
 * 7.9.7 hitboxes, which follow the visible building instead of the whole square box.
 */
internal object SpriteBounds {
    private val table: Map<Int, FloatArray> = mapOf(
        R.drawable.n_tree to floatArrayOf(0.144f, 0.078f, 0.861f, 0.972f),
        R.drawable.lm_post_ruin to floatArrayOf(0.113f, 0.150f, 0.867f, 0.981f),
        R.drawable.lm_post to floatArrayOf(0.133f, 0.112f, 0.871f, 0.981f),
        R.drawable.b_post to floatArrayOf(0.131f, 0.258f, 0.869f, 0.977f),
        R.drawable.decor_palm to floatArrayOf(0.135f, 0.190f, 0.900f, 0.980f),
        R.drawable.fief_tower_small to floatArrayOf(0.291f, 0.130f, 0.709f, 0.957f),
        R.drawable.lm_lighthouse to floatArrayOf(0.206f, 0.104f, 0.813f, 0.985f),
        R.drawable.b_lighthouse to floatArrayOf(0.185f, 0.088f, 0.800f, 0.977f),
        R.drawable.n_crates to floatArrayOf(0.061f, 0.094f, 0.939f, 0.978f),
        R.drawable.lm_library_ruin to floatArrayOf(0.131f, 0.104f, 0.865f, 0.981f),
        R.drawable.lm_library to floatArrayOf(0.158f, 0.058f, 0.827f, 0.977f),
        R.drawable.b_library to floatArrayOf(0.169f, 0.123f, 0.858f, 0.973f),
        R.drawable.lm_campfire to floatArrayOf(0.085f, 0.588f, 0.919f, 0.988f),
        R.drawable.b_workshop to floatArrayOf(0.119f, 0.246f, 0.881f, 0.981f),
        R.drawable.b_hall to floatArrayOf(0.119f, 0.235f, 0.888f, 0.977f),
        R.drawable.n_rowboat to floatArrayOf(0.089f, 0.294f, 0.906f, 0.956f),
        R.drawable.lm_jetty_ruin to floatArrayOf(0.104f, 0.492f, 0.919f, 0.985f),
        R.drawable.b_jetty to floatArrayOf(0.112f, 0.331f, 0.896f, 0.981f),
        R.drawable.lm_jetty to floatArrayOf(0.135f, 0.162f, 0.896f, 0.981f),
        R.drawable.decor_hammock to floatArrayOf(0.045f, 0.275f, 0.950f, 0.975f),
        R.drawable.fief_hut_stage1 to floatArrayOf(0.109f, 0.240f, 0.889f, 0.938f),
        R.drawable.b_house to floatArrayOf(0.119f, 0.204f, 0.865f, 0.973f),
        R.drawable.fief_hut_stage3 to floatArrayOf(0.104f, 0.094f, 0.700f, 0.734f),
        R.drawable.decor_flowers to floatArrayOf(0.095f, 0.455f, 0.890f, 0.980f),
        R.drawable.decor_bench to floatArrayOf(0.060f, 0.275f, 0.930f, 0.970f),
        R.drawable.decor_lantern to floatArrayOf(0.247f, 0.050f, 0.738f, 0.975f),
        R.drawable.decor_flag to floatArrayOf(0.030f, 0.075f, 0.810f, 0.985f),
        R.drawable.decor_campfire to floatArrayOf(0.095f, 0.325f, 0.905f, 0.980f),
        R.drawable.decor_fountain to floatArrayOf(0.080f, 0.210f, 0.905f, 0.980f),
        R.drawable.decor_windmill to floatArrayOf(0.215f, 0.095f, 0.790f, 0.985f),
        R.drawable.decor_maibaum to floatArrayOf(0.165f, 0.075f, 0.815f, 0.985f),
        R.drawable.place_home to floatArrayOf(0.093f, 0.400f, 0.923f, 0.980f),
        R.drawable.place_bude to floatArrayOf(0.208f, 0.097f, 0.782f, 0.983f),
        R.drawable.place_uni to floatArrayOf(0.070f, 0.433f, 0.877f, 0.990f),
        R.drawable.place_hut to floatArrayOf(0.100f, 0.480f, 0.907f, 0.983f),
        R.drawable.place_club to floatArrayOf(0.073f, 0.463f, 0.907f, 0.980f),
        R.drawable.place_station to floatArrayOf(0.067f, 0.483f, 0.900f, 0.973f),
        R.drawable.place_city to floatArrayOf(0.103f, 0.427f, 0.907f, 0.977f),
        R.drawable.place_desk to floatArrayOf(0.090f, 0.380f, 0.907f, 0.987f),
    )
    /** Fallback for a sprite without a measured box: the middle of the lower box. */
    private val fallback = floatArrayOf(.2f, .25f, .8f, 1f)
    fun of(res: Int): FloatArray = table[res] ?: fallback
}
