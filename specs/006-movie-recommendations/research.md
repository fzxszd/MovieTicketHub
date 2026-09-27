# Research: 基于收藏的个性化电影推荐

## 1. 算法

**Decision**: 后端版本化、确定性的内容相似度，不引入 ML 框架或随机噪声。

**Rationale**: 当前数据规模小，收藏和公开特征足以产生可解释结果；确定性便于测试和回滚。

## 2. 个性化阈值

**Decision**: 至少 2 部已确认收藏且能形成至少一个有效特征权重才个性化。

**Rationale**: 单一或无特征收藏容易产生虚假精确感；不足时诚实 fallback 更有用。

## 3. 特征与评分

**Decision**: 使用公开类型/主题为主要相似度，热度与新鲜度为次要项；权重服务端版本化。稳定 tie-break 为 score、popularity、movieId。

**Rationale**: 原因可由实际贡献特征生成，并保证相同输入顺序不跳动。

## 4. 排除规则

**Decision**: 新发现默认排除已收藏、已购买和不可用电影；重复候选按 movieId 合并。

**Rationale**: 避免把已知内容伪装为发现，同时保持行为一致。

## 5. 原因

**Decision**: 仅使用当前账号收藏形成的聚合特征和公开电影特征；取最高真实贡献生成短原因。不能解释则不用 personalized 标签。

**Rationale**: 防止编造关联或泄露其他用户信息。

## 6. 备用推荐

**Decision**: 游客、收藏/特征不足、服务失败或空结果时，返回稳定热门或正在上映内容，并给出机器可读 fallbackReason 和用户标签。

**Rationale**: 不出现空白，又不会把通用内容伪装成个性化。

## 7. 收藏同步边界

**Decision**: 只读取 005 confirmed revision；pending 时继续使用最后确认版本并显示提示，confirmed revision 变化使缓存失效。

**Rationale**: 未同步意图不能冒充跨设备权威偏好。

## 8. 缓存与隔离

**Decision**: 服务端按 userId+favoriteRevision+catalogVersion+algorithmVersion 缓存；客户端只保留当前账号内存状态，切号先清除。

**Rationale**: 准确失效并杜绝上一账号闪现。

## 9. 指标

**Decision**: 记录 recommendationId、版本、source、位置和 CLICK/FAVORITE/PURCHASE 聚合事件；不保存原始收藏列表、原因来源标题或敏感身份。

**Rationale**: 足以评估质量，同时遵循数据最小化。

## 10. 目录来源

**Decision**: 后端维护公开电影目录快照及 catalogVersion；刷新失败保留上一份仍可用快照并标记陈旧，完全不可用则明确 empty。

**Rationale**: 推荐服务需要稳定候选，不能让 Android 每次提交任意候选或特征。
