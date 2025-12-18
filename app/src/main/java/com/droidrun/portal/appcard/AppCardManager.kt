package com.droidrun.portal.appcard

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * App Cards 管理器
 * 负责 App Cards 的增删改查和文件存储
 */
class AppCardManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AppCardManager"
        private const val APP_CARDS_DIR = "app_cards"
        private const val INDEX_FILE = "index.json"
        
        @Volatile
        private var instance: AppCardManager? = null
        
        fun getInstance(context: Context): AppCardManager {
            return instance ?: synchronized(this) {
                instance ?: AppCardManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val appCardsDir: File
    private val indexFile: File
    private val cardsCache = mutableMapOf<String, AppCard>()
    
    init {
        // 初始化目录
        appCardsDir = File(context.getExternalFilesDir(null), APP_CARDS_DIR)
        if (!appCardsDir.exists()) {
            appCardsDir.mkdirs()
        }
        indexFile = File(appCardsDir, INDEX_FILE)
        
        // 加载索引
        loadIndex()
    }
    
    /**
     * 加载索引文件
     */
    private fun loadIndex() {
        try {
            if (indexFile.exists()) {
                val json = JSONObject(indexFile.readText())
                val cardsArray = json.getJSONArray("cards")
                
                cardsCache.clear()
                for (i in 0 until cardsArray.length()) {
                    val cardJson = cardsArray.getJSONObject(i)
                    val card = AppCard.fromJson(cardJson)
                    cardsCache[card.id] = card
                }
                
                Log.i(TAG, "Loaded ${cardsCache.size} app cards from index")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading index", e)
        }
    }
    
    /**
     * 保存索引文件
     */
    private fun saveIndex() {
        try {
            val json = JSONObject().apply {
                put("version", 1)
                put("cards", JSONArray().apply {
                    cardsCache.values.forEach { card ->
                        put(card.toJson())
                    }
                })
            }
            
            indexFile.writeText(json.toString(2))
            Log.i(TAG, "Saved ${cardsCache.size} app cards to index")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving index", e)
        }
    }
    
    /**
     * 添加或更新 App Card
     */
    fun saveAppCard(card: AppCard): Boolean {
        return try {
            // 保存到缓存
            cardsCache[card.id] = card
            
            // 保存索引
            saveIndex()
            
            // 保存 Markdown 文件
            val mdFile = File(appCardsDir, "${card.id}.md")
            mdFile.writeText(card.content)
            
            Log.i(TAG, "Saved app card: ${card.title} (${card.packageName})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving app card", e)
            false
        }
    }
    
    /**
     * 删除 App Card
     */
    fun deleteAppCard(cardId: String): Boolean {
        return try {
            cardsCache.remove(cardId)
            saveIndex()
            
            val mdFile = File(appCardsDir, "$cardId.md")
            if (mdFile.exists()) {
                mdFile.delete()
            }
            
            Log.i(TAG, "Deleted app card: $cardId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting app card", e)
            false
        }
    }
    
    /**
     * 获取指定 ID 的 App Card
     */
    fun getAppCard(cardId: String): AppCard? {
        return cardsCache[cardId]
    }
    
    /**
     * 获取指定包名的所有 App Cards
     */
    fun getAppCardsByPackage(packageName: String): List<AppCard> {
        return cardsCache.values.filter { it.packageName == packageName }
    }
    
    /**
     * 根据关键词搜索匹配的 App Card
     */
    fun searchAppCards(packageName: String, keyword: String): List<AppCard> {
        val cards = getAppCardsByPackage(packageName)
        
        if (keyword.isBlank()) {
            return cards
        }
        
        // 关键词匹配评分
        return cards.map { card ->
            val score = calculateMatchScore(card, keyword)
            Pair(card, score)
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
    }
    
    /**
     * 计算关键词匹配分数
     */
    private fun calculateMatchScore(card: AppCard, keyword: String): Int {
        var score = 0
        val lowerKeyword = keyword.lowercase()
        
        // 标题完全匹配
        if (card.title.lowercase() == lowerKeyword) {
            score += 100
        }
        // 标题包含关键词
        else if (card.title.lowercase().contains(lowerKeyword)) {
            score += 50
        }
        
        // 关键词列表匹配
        card.keywords.forEach { cardKeyword ->
            val lowerCardKeyword = cardKeyword.lowercase()
            if (lowerCardKeyword == lowerKeyword) {
                score += 80
            } else if (lowerCardKeyword.contains(lowerKeyword) || lowerKeyword.contains(lowerCardKeyword)) {
                score += 40
            }
        }
        
        // 内容包含关键词
        if (card.content.lowercase().contains(lowerKeyword)) {
            score += 10
        }
        
        return score
    }
    
    /**
     * 获取所有 App Cards
     */
    fun getAllAppCards(): List<AppCard> {
        return cardsCache.values.toList()
    }
    
    /**
     * 创建新的 App Card
     */
    fun createAppCard(
        packageName: String,
        title: String,
        keywords: List<String>,
        content: String
    ): AppCard {
        val card = AppCard(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            title = title,
            keywords = keywords,
            content = content
        )
        saveAppCard(card)
        return card
    }
    
    /**
     * 更新 App Card
     */
    fun updateAppCard(
        cardId: String,
        title: String? = null,
        keywords: List<String>? = null,
        content: String? = null
    ): AppCard? {
        val existingCard = getAppCard(cardId) ?: return null
        
        val updatedCard = existingCard.copy(
            title = title ?: existingCard.title,
            keywords = keywords ?: existingCard.keywords,
            content = content ?: existingCard.content,
            updatedAt = System.currentTimeMillis()
        )
        
        saveAppCard(updatedCard)
        return updatedCard
    }
    
    /**
     * 初始化默认 App Cards
     */
    fun initializeDefaultCards() {
        // 美团外卖订单查询示例
        if (getAppCardsByPackage("com.sankuai.meituan").isEmpty()) {
            createAppCard(
                packageName = "com.sankuai.meituan",
                title = "查看最近的外卖订单",
                keywords = listOf("外卖", "订单", "查询", "历史"),
                content = """
# 美团 - 查看最近的外卖订单

## 操作流程
1. 启动美团 App
2. 点击底部导航栏的"订单"按钮（通常在右下角）
3. 在订单列表中查看最近的外卖订单
4. 可以点击具体订单查看详情

## UI 元素位置
- **底部导航栏**: 位于屏幕最底部
- **订单按钮**: 导航栏右侧，通常是第4个或第5个按钮
- **订单列表**: 订单按钮点击后显示在主界面中间区域

## 搜索语法
- 使用订单搜索框: 可以搜索商家名称或订单号
- 筛选器: 可以按"全部"、"待付款"、"待收货"等状态筛选

## 常见问题
- 如果订单列表为空，可能需要先登录账号
- 订单可能需要下拉刷新才能看到最新的
- 部分订单可能在"外卖"分类下，需要切换标签页

## 关键词
外卖、订单、查询、历史、配送
                """.trimIndent()
            )
            
            Log.i(TAG, "Initialized default app card for Meituan")
        }
    }
}