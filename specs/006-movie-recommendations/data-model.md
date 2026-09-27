# Data Model: 基于收藏的个性化电影推荐

## MovieCatalogEntry

`movieId` 主键（>0）、title、genres、themes、language、peopleTokens（可选公开特征）、popularity >=0、releaseDate、availability、catalogVersion、updatedAt。集合字段规范化、去重排序。

## Hybrid signals

Content signals are derived from each Maoyan catalog entry (`genres_json`, cast tokens, bounded popularity and `release_date`). Collaborative signals are computed at request time from account-scoped implicit interactions: confirmed favorites, paid orders and recommendation clicks. Raw interaction lists are not returned; only the final score, reason type and matched public feature labels are exposed.

## RecommendationProfile

运行时模型：userId、favoriteRevision、usableFavoriteCount、featureWeights、status (`READY|INSUFFICIENT|PENDING_SYNC`)。不得持久化原始令牌或其他用户信号。

## RecommendationRun

| Field | Rules |
|---|---|
| `id` | 高熵稳定 ID |
| `userId` | personalized 时当前用户；fallback 可为空 |
| `source` | `PERSONALIZED|FALLBACK_POPULAR|FALLBACK_NOW_PLAYING` |
| `algorithmVersion/catalogVersion` | 非空版本 |
| `favoriteRevision` | personalized 必填 |
| `fallbackReason` | `GUEST|INSUFFICIENT_FAVORITES|INSUFFICIENT_FEATURES|SERVICE_UNAVAILABLE|NO_PERSONALIZED_RESULTS` 或空 |
| `createdAt` | UTC |

## RecommendationItem

联合主键 `(runId,movieId)`；rank 从 1 连续；score 非负；reasonType (`SHARED_GENRE|SHARED_THEME|POPULAR|NOW_PLAYING`)；reasonText；matchedFeatures 只含公开特征。movieId 在一次 run 中唯一。

## RecommendationMetric

recommendationId、movieId、algorithmVersion、source、rank、action (`IMPRESSION|CLICK|FAVORITE|PURCHASE`)、occurredAt。相同客户端 eventId 幂等；不保存原始收藏或敏感凭据。

## API Models and Invariants

- `RecommendationResponse`: source、sourceLabel、personalizationState、versions、items、fallbackReason、retryable。
- 个性化要求：有效会话、>=2 个 usable confirmed favorites、至少一个特征、服务可用。
- 候选必须 AVAILABLE，按 movieId 去重；默认排除 confirmed favorites 和 PAID movieIds。
- 相同 favoriteRevision/catalogVersion/algorithmVersion 输入必须产生相同 Top-N 和顺序。
- 每个 PERSONALIZED item 至少一个真实原因；fallback 原因不得引用用户收藏。
- account switch/logout 后客户端状态先置 Loading/Guest，再加载新响应。
