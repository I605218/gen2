<template>
  <main class="app-shell">
    <header class="topbar panel">
      <div>
        <p class="eyebrow">Code Assistant Agent</p>
        <h1>多轮对话工作台</h1>
      </div>
      <div class="topbar-actions">
        <button v-if="currentUser" class="ghost" type="button" @click="activePage = 'knowledge'">知识库管理</button>
        <button v-if="currentUser" class="account-trigger" type="button" @click="activePage = 'profile'">
          <span class="avatar-badge">{{ userInitial }}</span>
          <span class="account-copy">
            <strong>{{ currentUser.nickname }}</strong>
            <small>@{{ currentUser.username }}</small>
          </span>
        </button>
        <button v-else class="primary" type="button" @click="openAuthModal('login')">登录 / 注册</button>
      </div>
    </header>

    <div v-if="notice.message" :class="['notice-toast', notice.type, notice.visible ? 'visible' : '']">
      <span>{{ notice.message }}</span>
      <button class="notice-toast-close" type="button" @click="clearNotice" aria-label="关闭提示">×</button>
    </div>

    <section v-if="activePage === 'workspace'" class="chat-layout panel">
      <aside class="chat-sidebar">
        <div class="sidebar-head">
          <div>
            <p class="eyebrow small">会话</p>
            <h2>对话列表</h2>
          </div>
          <button class="primary small-button" type="button" @click="createConversation">新建</button>
        </div>

        <div class="sidebar-summary">
          <strong>当前会话</strong>
          <p>{{ activeConversation?.title || '新对话' }}</p>
          <small>{{ activeConversationMessageCount }} 条用户消息</small>
        </div>

        <div class="conversation-list">
          <div
            v-for="item in conversations"
            :key="item.sessionId"
            :class="['conversation-card', activeConversationId === item.sessionId ? 'active' : '']"
            @click="selectConversation(item.sessionId)"
          >
            <div class="conversation-card-head">
              <strong>{{ item.title || '未命名会话' }}</strong>
              <span>{{ item.messageCount }} 条</span>
            </div>
            <p>{{ item.answerContent || item.summary || '开始聊天后，这里会显示会话摘要。' }}</p>
            <small>{{ item.updatedAt ? formatHistoryTime(item.updatedAt) : '刚刚' }}</small>
            <div class="conversation-actions">
              <button class="conversation-menu-trigger" type="button" @click.stop="toggleConversationMenu(item.sessionId)">···</button>
              <div v-if="openConversationMenuId === item.sessionId" class="conversation-menu" @click.stop>
                <button type="button" @click="promptRenameConversation(item.sessionId)">重命名</button>
                <button type="button" class="danger" @click="confirmDeleteConversation(item.sessionId)">删除</button>
              </div>
            </div>
          </div>

          <div v-if="!conversations.length" class="empty-state compact">
            <strong>暂无会话</strong>
            <p>点击“新建”或者直接发送第一条消息，系统会自动建立聊天记录。</p>
          </div>
        </div>
      </aside>

      <section class="chat-panel">
        <div class="chat-messages-shell">
          <div ref="chatScrollRef" class="chat-messages" @scroll="handleChatScroll">
            <div v-if="!chatMessages.length" class="empty-state chat-empty">
              <strong>还没有消息</strong>
              <p>先输入一个问题，Agent 会像聊天软件里的智能助手一样回应你。</p>
            </div>

            <template v-for="group in groupMessages(chatMessages)" :key="`${group.role}-${group.items[0]?.id}`">
              <article v-for="message in group.items" :key="message.id" :class="['chat-row', message.role]">
                <div class="message-line" :class="message.role">
                  <div class="bubble-wrap">
                    <div class="bubble-time">{{ formatHistoryTime(message.createdAt) }}</div>
                    <div class="bubble" v-html="renderMarkdown(message.content)"></div>
                  </div>
                  <button class="copy-trigger" type="button" @click="copyMessageContent(message.content)" :aria-label="`复制${message.role === 'user' ? '用户' : 'Agent'}消息`">⧉</button>
                </div>
              </article>
              <article v-if="pendingAgentMessage && group.role === 'user' && group.items[group.items.length - 1]?.id === chatMessages[chatMessages.length - 1]?.id" class="chat-row agent pending-row">
                <div class="message-line agent">
                  <div class="bubble-wrap">
                    <div class="bubble-time">Agent 思考中</div>
                    <div class="bubble thinking-bubble">
                      <span class="thinking-dots"><i></i><i></i><i></i></span>
                    </div>
                  </div>
                  <button class="copy-trigger disabled" type="button" disabled aria-label="复制Agent消息">⧉</button>
                </div>
              </article>
            </template>
          </div>
          <button v-if="showScrollToBottomButton" class="scroll-bottom-button" type="button" @click="scrollToBottom" aria-label="回到最新消息">
            <span class="scroll-bottom-icon">⌄</span>
          </button>
        </div>

        <div class="chat-composer">
          <div class="composer-card">
            <form class="composer-form" @submit.prevent="submitRequest">
              <div class="composer-input-shell">
                <textarea
                  v-model="form.question"
                  rows="4"
                  placeholder="输入你的问题，Enter 发送，Shift+Enter 换行。"
                  @keydown.enter.exact.prevent="submitRequest"
                ></textarea>
                <div class="composer-input-footer">
                  <div class="composer-left-controls">
                    <button v-for="mode in modes" :key="mode.id" type="button" :class="['ghost', activeMode === mode.id ? 'active' : '']" @click="activeMode = mode.id">
                      {{ mode.label }}
                    </button>
                    <div class="cross-share-controls">
                      <label class="cross-share-toggle" title="开启后会在当前回答中参考你其他历史会话的摘要信息">
                        <input v-model="form.enableCrossConversationKnowledge" type="checkbox" />
                        <span>跨对话知识共享</span>
                      </label>
                      <label v-if="form.enableCrossConversationKnowledge" class="share-mode-select">
                        <span>模式</span>
                        <select v-model="form.crossConversationShareMode">
                          <option value="BALANCED">平衡</option>
                          <option value="RECENT_FIRST">最近优先</option>
                          <option value="RELEVANCE_FIRST">相关优先</option>
                        </select>
                      </label>
                      <label v-if="form.enableCrossConversationKnowledge" class="share-limit-select">
                        <span>范围</span>
                        <input v-model.number="form.crossConversationShareLimit" type="range" min="1" max="8" />
                        <small>{{ form.crossConversationShareLimit }} 条</small>
                      </label>
                    </div>
                    <button v-if="currentUser" class="ghost" type="button" @click="activePage = 'skills'">技能管理</button>
                    <span class="session-pill">当前会话 {{ activeConversationMessageCount }} 条用户消息</span>
                  </div>
                  <div class="composer-right-actions">
                    <label class="composer-icon-button" title="上传附件">
                      <input type="file" class="hidden-file-input" multiple @change="handleComposerAttachment" />
                      <span>📎</span>
                    </label>
                    <button class="composer-icon-button send" type="submit" :disabled="loading || cooldownSeconds > 0" :title="loading ? '发送中' : '发送'">
                      <span>{{ loading ? '…' : '↑' }}</span>
                    </button>
                  </div>
                </div>
              </div>

              <template v-if="activeMode === 'tool'">
                <div class="tool-panel">
                  <button class="tool-panel-trigger" type="button" @click="toggleToolDropdown">
                    <span class="tool-trigger-copy">
                      <strong>工具调用</strong>
                      <small>{{ selectedToolIds.length ? `已选 ${selectedToolIds.length} 项` : '请选择本次要调用的工具' }}</small>
                    </span>
                    <span class="tool-trigger-chevron">⌄</span>
                  </button>
                  <div v-if="toolDropdownOpen" class="tool-dropdown" @click.stop>
                    <div v-for="group in toolCatalog" :key="group.group" class="tool-group-block">
                      <div class="tool-group-title">{{ group.group }}</div>
                      <label v-for="tool in group.tools" :key="tool.id" class="tool-dropdown-item">
                        <input :checked="selectedToolIds.includes(tool.id)" type="checkbox" @change="toggleToolSelection(tool.id)" />
                        <span>{{ tool.name }}</span>
                        <button class="tool-mini-info" type="button" @click.stop="toggleToolInfo(tool.id)">?</button>
                        <div v-if="toolInfoId === tool.id" class="tool-tooltip tool-row-tooltip" @click.stop>
                          <strong>{{ tool.name }}</strong>
                          <p>{{ tool.detail }}</p>
                        </div>
                      </label>
                    </div>
                  </div>
                </div>
                <div v-if="currentUser && skills.length" class="skill-picker">
                  <strong>本次使用的 Skill</strong>
                  <label v-for="skill in skills" :key="skill.id" class="skill-choice">
                    <input v-model="selectedSkillIds" type="checkbox" :value="skill.id" :disabled="!skill.enabled" />
                    <span>{{ skill.name }}</span>
                  </label>
                </div>
                <label class="code-field tool-code-field">
                  <span>代码</span>
                  <textarea v-model="form.code" rows="8" placeholder="请把需要分析的代码粘贴到这里"></textarea>
                </label>
                <div class="tool-meta-grid">
                  <label>
                    <span>编程语言</span>
                    <select v-model="form.language">
                      <option value="Java">Java</option>
                      <option value="Python">Python</option>
                      <option value="JavaScript">JavaScript</option>
                      <option value="TypeScript">TypeScript</option>
                      <option value="C">C</option>
                      <option value="C++">C++</option>
                      <option value="Go">Go</option>
                      <option value="Rust">Rust</option>
                      <option value="其他">其他</option>
                    </select>
                  </label>
                  <label v-if="form.language === '其他'">
                    <span>自定义语言</span>
                    <input v-model="form.customLanguage" type="text" placeholder="请输入语言名称" />
                  </label>
                  <label>
                    <span>错误信息</span>
                    <input v-model="form.errorMessage" type="text" placeholder="如 NullPointerException" />
                  </label>
                  <label>
                    <span>知识点</span>
                    <input v-model="form.knowledgePoint" type="text" placeholder="如 动态规划" />
                  </label>
                </div>
              </template>

            </form>
          </div>
        </div>
      </section>
    </section>

    <section v-else-if="activePage === 'knowledge'" class="profile-page panel">
      <div class="profile-hero">
        <div>
          <p class="eyebrow small">RAG Knowledge Center</p>
          <h2>知识库管理</h2>
          <p class="profile-hero-copy">支持文档导入、切块索引、检索预览、引用来源展示与样例数据一键初始化。</p>
        </div>
        <div class="profile-hero-actions">
          <button class="ghost" type="button" @click="seedKnowledgeSamples">导入示例知识</button>
          <button class="ghost" type="button" @click="activePage = 'workspace'">返回聊天</button>
        </div>
      </div>
      <div v-if="currentUser" class="knowledge-layout">
        <article class="profile-card result-card">
          <h3>{{ knowledgeEditingId ? '编辑知识文档' : '新增知识文档' }}</h3>
          <div class="knowledge-import-box">
            <div>
              <strong>导入外部文档</strong>
              <p>支持 .txt / .md / .markdown / .csv / .json / .html / .xml / .log / .java / .py / .js / .ts / .vue 等文本类文档，可一次选择多个文件。</p>
            </div>
            <label class="knowledge-file-picker">
              <span>{{ knowledgeImportLoading ? '导入中…' : '选择文档' }}</span>
              <input type="file" multiple accept=".txt,.md,.markdown,.csv,.json,.html,.htm,.xml,.log,.java,.py,.js,.ts,.tsx,.jsx,.vue,.css,.scss,.sql,.yml,.yaml,.properties" :disabled="knowledgeImportLoading" @change="handleKnowledgeFileImport" />
            </label>
          </div>
          <div v-if="knowledgeImportFiles.length" class="knowledge-import-preview">
            <div v-for="file in knowledgeImportFiles" :key="file.name" class="knowledge-import-file">
              <strong>{{ file.name }}</strong>
              <small>{{ file.status }} · {{ file.size }}</small>
            </div>
          </div>
          <form class="auth-form" @submit.prevent="saveKnowledgeDocument">
            <label><span>标题</span><input v-model="knowledgeForm.title" type="text" placeholder="如：Spring Boot 接口设计规范" /></label>
            <label><span>来源名称</span><input v-model="knowledgeForm.sourceName" type="text" placeholder="如：项目文档 / 课程笔记" /></label>
            <label><span>来源类型</span><input v-model="knowledgeForm.sourceType" type="text" placeholder="md / txt / pdf / 网页整理" /></label>
            <label><span>标签</span><input v-model="knowledgeForm.tags" type="text" placeholder="用逗号或中文逗号分隔，例如 前端,后端,RAG" /></label>
            <label><span>摘要</span><textarea v-model="knowledgeForm.summary" rows="3" placeholder="文档核心内容概述"></textarea></label>
            <label><span>别名</span><textarea v-model="knowledgeForm.aliasText" rows="2" placeholder="用竖线或换行分隔多个别名"></textarea></label>
            <label><span>分类</span><textarea v-model="knowledgeForm.categoryText" rows="2" placeholder="如 前端 | 后端 | 算法"></textarea></label>
            <label><span>参考链接 / 出处</span><textarea v-model="knowledgeForm.referenceText" rows="2" placeholder="可填文档章节、URL、书籍章节等"></textarea></label>
            <label><span>内容</span><textarea v-model="knowledgeForm.content" rows="12" placeholder="粘贴知识原文，保存后会自动切块并建立检索索引"></textarea></label>
            <label class="skill-enabled"><input v-model="knowledgeForm.enabled" type="checkbox" /> 启用</label>
            <div class="composer-actions">
              <button class="primary" type="submit" :disabled="knowledgeLoading">{{ knowledgeLoading ? '保存中…' : '保存文档' }}</button>
              <button class="ghost" type="button" @click="resetKnowledgeForm">清空</button>
            </div>
          </form>
        </article>
        <article class="profile-card result-card">
          <div class="card-head-inline">
            <h3>知识文档列表</h3>
            <div class="card-head-actions">
              <button class="ghost small-button" type="button" @click="loadKnowledgeDocuments">刷新</button>
              <button class="ghost small-button" type="button" @click="seedKnowledgeSamples">导入样例</button>
            </div>
          </div>
          <div v-if="!knowledgeDocuments.length" class="empty-state compact">暂无知识文档，先添加一个或导入样例吧。</div>
          <div v-for="doc in knowledgeDocuments" :key="doc.id" class="skill-list-item knowledge-list-item">
            <div>
              <strong>{{ doc.title }}</strong>
              <p>{{ doc.summary || doc.tags || '暂无摘要' }}</p>
              <small>{{ doc.enabled ? '已启用' : '已停用' }} · {{ doc.chunkCount || 0 }} 个切片 · {{ doc.totalChars || 0 }} 字</small>
            </div>
            <div class="skill-list-actions">
              <button class="ghost small-button" type="button" @click="editKnowledgeDocument(doc)">编辑</button>
              <button class="ghost small-button" type="button" @click="previewKnowledgeDocument(doc)">查看</button>
              <button class="ghost small-button danger-button" type="button" @click="deleteKnowledgeDocument(doc.id)">删除</button>
            </div>
          </div>
        </article>
        <article class="profile-card result-card knowledge-search-panel">
          <h3>知识检索与引用</h3>
          <div class="knowledge-search-bar">
            <input v-model="knowledgeSearchQuery" type="text" placeholder="输入问题或关键词进行检索，例如：RAG、切块、引用来源" @keyup.enter="searchKnowledge" />
            <button class="primary" type="button" @click="searchKnowledge">检索</button>
          </div>
          <div v-if="knowledgeSearchResult?.results?.length" class="knowledge-search-results">
            <article v-for="(item, index) in knowledgeSearchResult.results" :key="item.chunkId || `${item.documentId}-${index}`" class="knowledge-search-item">
              <div class="knowledge-search-item-head">
                <strong>[{{ index + 1 }}] {{ item.title }}</strong>
                <span>score {{ formatScore(item.score) }}</span>
              </div>
              <p class="knowledge-search-meta">
                <span v-if="item.sourceName">来源：{{ item.sourceName }}</span>
                <span v-if="item.sourceType">类型：{{ item.sourceType }}</span>
                <span v-if="item.tags">标签：{{ item.tags }}</span>
              </p>
              <p v-if="item.summary" class="knowledge-search-summary">摘要：{{ item.summary }}</p>
              <p class="knowledge-search-content">{{ item.content }}</p>
              <small v-if="item.references">参考：{{ item.references }}</small>
              <small v-else>切片区间：{{ item.startOffset }} - {{ item.endOffset }}</small>
            </article>
          </div>
          <div v-else class="empty-state compact">输入问题后可以查看当前知识库的匹配片段与引用来源。</div>
        </article>
      </div>
      <div v-else class="empty-state profile-empty"><strong>请先登录</strong><p>登录后才能管理自己的知识库。</p></div>
    </section>

    <section v-else-if="activePage === 'skills'" class="profile-page panel">
      <div class="profile-hero">
        <div>
          <p class="eyebrow small">Skill Center</p>
          <h2>技能管理</h2>
          <p class="profile-hero-copy">添加、编辑和删除你自己的 Agent Skill，聊天时可按需选择使用。</p>
        </div>
        <button class="ghost" type="button" @click="activePage = 'workspace'">返回聊天</button>
      </div>
      <div v-if="currentUser" class="skill-page-grid">
        <article class="profile-card result-card">
          <h3>{{ skillEditingId ? '编辑 Skill' : '新增 Skill' }}</h3>
          <form class="auth-form" @submit.prevent="saveSkill">
            <label><span>名称</span><input v-model="skillForm.name" type="text" placeholder="如：算法讲解风格" /></label>
            <label><span>描述</span><input v-model="skillForm.description" type="text" placeholder="简要说明这个 skill 的用途" /></label>
            <div class="skill-content-tabs">
              <button type="button" :class="['ghost', skillForm.contentMode === 'manual' ? 'active' : '']" @click="skillForm.contentMode = 'manual'">手动添加</button>
              <button type="button" :class="['ghost', skillForm.contentMode === 'file' ? 'active' : '']" @click="skillForm.contentMode = 'file'">文件添加</button>
            </div>
            <template v-if="skillForm.contentMode === 'manual'">
              <label><span>Skill 内容</span><textarea v-model="skillForm.content" rows="8" placeholder="写下你希望 Agent 遵循的规则、步骤、风格或约束"></textarea></label>
            </template>
            <template v-else>
              <label class="skill-file-picker">
                <span>上传 Skill 文件</span>
                <input type="file" accept=".md,.markdown,.txt" @change="handleSkillFileSelect" />
                <small>{{ skillForm.fileName || '支持 .md / .markdown / .txt，上传后会自动填充内容' }}</small>
              </label>
              <label><span>文件内容预览</span><textarea v-model="skillForm.content" rows="8" placeholder="上传后会自动填充，也可以在这里微调"></textarea></label>
            </template>
            <label class="skill-enabled"><input v-model="skillForm.enabled" type="checkbox" /> 启用</label>
            <div class="composer-actions">
              <button class="primary" type="submit" :disabled="skillLoading">{{ skillLoading ? '保存中…' : '保存 Skill' }}</button>
              <button class="ghost" type="button" @click="resetSkillForm">取消编辑</button>
            </div>
          </form>
        </article>
        <article class="profile-card result-card">
          <h3>我的 Skill</h3>
          <div v-if="!skills.length" class="empty-state compact">暂无 Skill，先新增一个吧。</div>
          <div v-for="skill in skills" :key="skill.id" class="skill-list-item">
            <div><strong>{{ skill.name }}</strong><p>{{ skill.description || '暂无描述' }}</p><small>{{ skill.enabled ? '已启用' : '已停用' }}</small></div>
            <div class="skill-list-actions">
              <button class="ghost small-button" type="button" @click="editSkill(skill)">编辑</button>
              <button class="ghost small-button danger-button" type="button" @click="deleteSkill(skill.id)">删除</button>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="empty-state profile-empty"><strong>请先登录</strong><p>登录后才能管理自己的 Skill。</p></div>
    </section>

    <section v-else class="profile-page panel">
      <div class="profile-hero">
        <div>
          <p class="eyebrow small">Account Center</p>
          <h2>个人中心</h2>
          <p class="profile-hero-copy">管理你的账号资料、密码和登录状态。</p>
        </div>
        <button class="ghost" type="button" @click="activePage = 'workspace'">返回聊天</button>
      </div>

      <div v-if="currentUser" class="profile-layout">
        <article class="profile-summary result-card">
          <div class="profile-summary-top">
            <span class="profile-avatar">{{ userInitial }}</span>
            <div>
              <h3>{{ currentUser.nickname }}</h3>
              <p>@{{ currentUser.username }}</p>
            </div>
          </div>
          <div class="profile-badges">
            <span>已登录</span>
            <span>多轮对话工作台</span>
          </div>
        </article>

        <div class="profile-grid">
          <article class="result-card profile-card">
            <h3>个人信息管理</h3>
            <form class="auth-form" @submit.prevent="updateProfile">
              <label>
                <span>用户名</span>
                <input :value="currentUser.username" type="text" disabled />
              </label>
              <label>
                <span>昵称</span>
                <input v-model="profileForm.nickname" type="text" placeholder="请输入新的昵称" />
              </label>
              <button class="primary" type="submit" :disabled="profileLoading">{{ profileLoading ? '保存中…' : '保存个人信息' }}</button>
            </form>
          </article>

          <article class="result-card profile-card">
            <h3>账户操作</h3>
            <div class="profile-actions">
              <button class="ghost danger-button" type="button" @click="logout">退出登录</button>
            </div>
          </article>
        </div>
      </div>

      <div v-else class="empty-state profile-empty">
        <strong>你还没有登录</strong>
        <p>登录后可以修改昵称、退出登录并继续管理你的会话。</p>
      </div>
    </section>

    <div v-if="copyToast.visible" :class="['copy-toast', copyToast.visible ? 'visible' : '']">{{ copyToast.message }}</div>

    <div v-if="showAuthDialog" class="modal-mask" @click.self="closeAuthModal">
      <div class="modal-card panel">
        <div class="section-title">
          <h2>{{ authMode === 'login' ? '登录账号' : '注册账号' }}</h2>
          <button class="ghost small-button" type="button" @click="closeAuthModal">关闭</button>
        </div>
        <form class="auth-form" @submit.prevent="submitAuth">
          <label>
            <span>用户名</span>
            <input v-model="authForm.username" type="text" placeholder="请输入用户名" />
          </label>
          <label v-if="authMode === 'register'">
            <span>昵称</span>
            <input v-model="authForm.nickname" type="text" placeholder="展示给自己的昵称" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="authForm.password" type="password" placeholder="请输入密码" />
          </label>
          <button class="primary" type="submit" :disabled="authLoading">{{ authLoading ? '提交中…' : authMode === 'login' ? '登录' : '注册并登录' }}</button>
          <button class="ghost" type="button" @click="toggleAuthMode">{{ authMode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}</button>
        </form>
      </div>
    </div>
  </main>
</template>

<script setup>
import katex from 'katex'
import 'katex/dist/katex.min.css'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'

const SESSION_STORAGE_KEY = 'code-assistant-agent-session-id'
const AUTH_TOKEN_STORAGE_KEY = 'code-assistant-agent-auth-token'
const AUTH_USER_STORAGE_KEY = 'code-assistant-agent-auth-user'

const modes = [
  { id: 'natural', label: '自然语言' },
  { id: 'tool', label: '工具管理' },
]
const activePage = ref('workspace')
const showAuthDialog = ref(false)
const activeMode = ref('natural')
const authMode = ref('login')
const loading = ref(false)
const authLoading = ref(false)
const profileLoading = ref(false)
const cooldownSeconds = ref(0)
const response = ref(null)
const notice = reactive({ type: '', message: '', visible: false })
const copyToast = reactive({ visible: false, message: '' })
const conversations = ref([])
const chatMessages = ref([])
const pendingUserMessage = ref('')
const pendingAgentMessage = ref(false)
const activeConversationId = ref(loadSessionId())
const authToken = ref(loadAuthToken())
const currentUser = ref(loadAuthUser())
const quickPrompts = ref([])
const chatScrollRef = ref(null)
const sessionState = reactive({ sessionId: '', messageCount: 0, latestTaskType: null, latestPrompt: null, latestAnswer: null, hasPinnedRecord: false })
const form = reactive({
  question: '',
  code: '',
  language: 'Java',
  customLanguage: '',
  errorMessage: '',
  knowledgePoint: '',
  enableCrossConversationKnowledge: false,
  crossConversationShareMode: 'BALANCED',
  crossConversationShareLimit: 3,
})
const authForm = reactive({ username: '', nickname: '', password: '' })
const profileForm = reactive({ nickname: '' })
const skills = ref([])
const selectedSkillIds = ref([])
const skillLoading = ref(false)
const skillEditingId = ref(null)
const skillForm = reactive({ name: '', description: '', content: '', enabled: true, contentMode: 'manual', fileName: '' })
const knowledgeDocuments = ref([])
const knowledgeSearchResult = ref(null)
const knowledgeSearchQuery = ref('')
const knowledgeLoading = ref(false)
const knowledgeImportLoading = ref(false)
const knowledgeImportFiles = ref([])
const knowledgeEditingId = ref(null)
const knowledgeForm = reactive({ title: '', sourceName: '', sourceType: '', tags: '', summary: '', aliasText: '', categoryText: '', referenceText: '', content: '', enabled: true })

const activeConversation = computed(() => conversations.value.find((item) => item.sessionId === activeConversationId.value) || null)
const resolvedLanguage = computed(() => form.language === '其他' ? form.customLanguage.trim() : form.language)
const knowledgeDocumentCount = computed(() => knowledgeDocuments.value.length)
const userInitial = computed(() => (currentUser.value?.nickname || currentUser.value?.username || 'U').slice(0, 1).toUpperCase())
const selectedToolIds = ref(['code-complexity-tool', 'error-keyword-tool', 'learning-resource-tool'])
const toolInfoId = ref('')
const toolDropdownOpen = ref(false)
const toolCatalog = [
  { group: '基础工具', tools: [
    {
      id: 'code-complexity-tool',
      name: '代码复杂度分析',
      brief: '统计代码行数、循环数量等，辅助判断时间复杂度和可读性。',
      detail: '适合代码审查和算法题分析。会重点观察循环、嵌套结构和代码长度，帮助你快速判断复杂度风险。',
    },
    {
      id: 'error-keyword-tool',
      name: '报错关键词分析',
      brief: '根据报错信息匹配常见错误模式并给出定位方向。',
      detail: '适合空指针、越界、语法错误等报错场景。输入报错信息后，会先做关键词归类，再结合上下文分析原因。',
    },
    {
      id: 'learning-resource-tool',
      name: '学习资源推荐',
      brief: '根据知识点或问题类型推荐适合的学习方向与资料。',
      detail: '适合想补基础、找练习题或整理知识点的时候使用，会结合问题内容生成更偏学习路径的建议。',
    },
  ]},
  { group: '前端工具', tools: [
    {
      id: 'frontend-code-generation-tool',
      name: '前端代码生成',
      brief: '生成页面、组件、表单、样式与交互的前端实现建议。',
      detail: '适合要快速搭建 Vue、React、HTML/CSS 页面结构时使用，会给出页面骨架、组件拆分和状态组织建议。',
    },
    {
      id: 'frontend-bug-fix-tool',
      name: '前端代码纠错',
      brief: '定位前端渲染、状态、事件、样式等常见问题。',
      detail: '适合修复前端报错、页面异常、交互失效、样式错位等问题，会优先检查模板绑定和响应式状态。',
    },
    {
      id: 'frontend-refactor-tool',
      name: '前端重构优化',
      brief: '给出前端结构拆分、复用、性能与可维护性优化建议。',
      detail: '适合对已有前端代码进行模块化拆分、组件抽象、渲染优化和代码可维护性提升。',
    },
  ]},
  { group: '后端工具', tools: [
    {
      id: 'backend-code-generation-tool',
      name: '后端代码生成',
      brief: '生成控制层、业务层、持久层与接口骨架建议。',
      detail: '适合快速搭建后端接口、服务、仓库、DTO 与数据库协作代码，优先保证结构清晰和职责分离。',
    },
    {
      id: 'backend-bug-fix-tool',
      name: '后端代码纠错',
      brief: '定位接口、事务、SQL、空指针、字段映射等后端问题。',
      detail: '适合排查 Controller、Service、Repository、数据库以及异常栈中的后端错误。',
    },
    {
      id: 'backend-refactor-tool',
      name: '后端重构优化',
      brief: '提供后端分层、抽象、日志与异常处理优化建议。',
      detail: '适合对后端服务进行解耦、公共逻辑抽取、统一异常处理和性能优化。',
    },
  ]},
]
const submitUrl = computed(() => '/api/conversations/chat')
const submitPayload = computed(() => ({
  sessionId: activeConversationId.value,
  message: form.question,
  code: form.code,
  summary: null,
  taskType: form.taskType,
  language: resolvedLanguage.value || null,
  errorMessage: form.errorMessage || null,
  knowledgePoint: form.knowledgePoint || null,
  selectedTools: selectedToolIds.value,
  selectedSkillIds: selectedSkillIds.value,
  enableCrossConversationKnowledge: form.enableCrossConversationKnowledge,
  crossConversationShareMode: form.crossConversationShareMode,
  crossConversationShareLimit: form.crossConversationShareLimit,
}))
const activeConversationMessageCount = computed(() => chatMessages.value.filter((item) => item.role === 'user').length)
const showScrollToBottomButton = ref(false)
const openConversationMenuId = ref('')

watch(currentUser, (value) => {
  profileForm.nickname = value?.nickname || ''
}, { immediate: true })

function createSessionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `session-${crypto.randomUUID()}`
  }
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function loadSessionId() {
  const stored = window.localStorage.getItem(SESSION_STORAGE_KEY)
  if (stored) return stored
  const generated = createSessionId()
  window.localStorage.setItem(SESSION_STORAGE_KEY, generated)
  return generated
}

function persistSessionId(value) {
  activeConversationId.value = value
  window.localStorage.setItem(SESSION_STORAGE_KEY, value)
}

function loadAuthToken() {
  return window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY) || ''
}

function loadAuthUser() {
  const raw = window.localStorage.getItem(AUTH_USER_STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function persistAuth(token, user) {
  authToken.value = token
  currentUser.value = user
  if (token) {
    window.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token)
  } else {
    window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
  }
  if (user) {
    window.localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user))
  } else {
    window.localStorage.removeItem(AUTH_USER_STORAGE_KEY)
  }
}

function openAuthModal(mode = 'login') {
  authMode.value = mode
  showAuthDialog.value = true
}

function closeAuthModal() {
  showAuthDialog.value = false
}

function showNotice(type, message) {
  notice.type = type
  notice.message = message
  notice.visible = false
  window.clearTimeout(showNotice._timer)
  window.requestAnimationFrame(() => {
    notice.visible = true
  })
  showNotice._timer = window.setTimeout(() => {
    clearNotice()
  }, 2600)
}

function clearNotice() {
  window.clearTimeout(showNotice._timer)
  notice.visible = false
  window.setTimeout(() => {
    notice.type = ''
    notice.message = ''
  }, 160)
}

function toggleToolSelection(id) {
  selectedToolIds.value = selectedToolIds.value.includes(id)
    ? selectedToolIds.value.filter((toolId) => toolId !== id)
    : [...selectedToolIds.value, id]
}

function toggleToolDropdown() {
  toolDropdownOpen.value = !toolDropdownOpen.value
  if (!toolDropdownOpen.value) {
    toolInfoId.value = ''
  }
}

function closeToolDropdown() {
  toolDropdownOpen.value = false
  toolInfoId.value = ''
}

function toggleToolInfo(id) {
  toolInfoId.value = toolInfoId.value === id ? '' : id
}

function handleToolOutsideClick(event) {
  const target = event.target
  if (!(target instanceof Element)) return
  if (target.closest('.tool-panel') || target.closest('.tool-tooltip-inline')) return
  closeToolDropdown()
}

function showCopyToast(message = '已复制到剪切板') {
  copyToast.message = message
  copyToast.visible = true
  window.clearTimeout(showCopyToast._timer)
  showCopyToast._timer = window.setTimeout(() => {
    copyToast.visible = false
  }, 1600)
}

function formatHistoryTime(value) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInlineMarkdown(value) {
  let placeholderIndex = 0
  const mathPlaceholders = []

  const withMathPlaceholders = escapeHtml(value).replace(/\$([^$\n]+)\$/g, (_, expression) => {
    const placeholder = `@@MATH_PLACEHOLDER_${placeholderIndex}@@`
    const rendered = katex.renderToString(expression, { throwOnError: false, strict: 'ignore', output: 'html' })
    mathPlaceholders.push({ placeholder, rendered })
    placeholderIndex += 1
    return placeholder
  })

  let html = withMathPlaceholders
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[(.*?)\]\((.*?)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>')

  mathPlaceholders.forEach(({ placeholder, rendered }) => {
    html = html.replace(placeholder, `<span class="math-inline">${rendered}</span>`)
  })

  return html
}

function renderMarkdown(text) {
  const value = String(text || '')
  const lines = value.split('\n')
  const parts = []
  let paragraph = []
  let listItems = []
  let orderedItems = []
  let codeLines = []
  let tableLines = []
  let quoteLines = []
  let inCode = false

  const isTableRow = (line) => /^\|(.+)\|$/.test(line.trim())
  const isTableDivider = (line) => {
    const cells = line.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())
    return cells.length > 0 && cells.every((cell) => /^:?-{3,}:?$/.test(cell))
  }
  const splitTableCells = (line) => line.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())

  const flushParagraph = () => {
    if (!paragraph.length) return
    parts.push(`<p>${renderInlineMarkdown(paragraph.join(' '))}</p>`)
    paragraph = []
  }

  const flushList = () => {
    if (!listItems.length) return
    parts.push(`<ul>${listItems.map((item) => `<li>${renderInlineMarkdown(item)}</li>`).join('')}</ul>`)
    listItems = []
  }

  const flushOrderedList = () => {
    if (!orderedItems.length) return
    parts.push(`<ol>${orderedItems.map((item) => `<li>${renderInlineMarkdown(item)}</li>`).join('')}</ol>`)
    orderedItems = []
  }

  const flushCode = () => {
    if (!codeLines.length) return
    parts.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`)
    codeLines = []
  }

  const flushQuote = () => {
    if (!quoteLines.length) return
    parts.push(`<blockquote>${quoteLines.map((line) => `<p>${renderInlineMarkdown(line)}</p>`).join('')}</blockquote>`)
    quoteLines = []
  }

  const flushTable = () => {
    if (tableLines.length < 2) {
      if (tableLines.length) paragraph.push(...tableLines)
      tableLines = []
      return
    }
    const header = splitTableCells(tableLines[0])
    const divider = tableLines[1]
    if (!isTableDivider(divider)) {
      paragraph.push(...tableLines)
      tableLines = []
      return
    }
    const rows = tableLines.slice(2).filter(isTableRow).map(splitTableCells)
    const thead = `<thead><tr>${header.map((cell) => `<th>${renderInlineMarkdown(cell)}</th>`).join('')}</tr></thead>`
    const tbody = rows.length
      ? `<tbody>${rows.map((row) => `<tr>${header.map((_, index) => `<td>${renderInlineMarkdown(row[index] || '')}</td>`).join('')}</tr>`).join('')}</tbody>`
      : ''
    parts.push(`<div class="table-wrap"><table>${thead}${tbody}</table></div>`)
    tableLines = []
  }

  lines.forEach((rawLine) => {
    const trimmed = rawLine.trim()

    if (trimmed.startsWith('```')) {
      flushParagraph()
      flushList()
      flushOrderedList()
      flushTable()
      flushQuote()
      if (inCode) flushCode()
      inCode = !inCode
      return
    }

    if (inCode) {
      codeLines.push(rawLine)
      return
    }

    if (trimmed.startsWith('>')) {
      flushParagraph()
      flushList()
      flushTable()
      quoteLines.push(trimmed.replace(/^>+\s?/, ''))
      return
    }

    if (quoteLines.length) {
      flushQuote()
    }

    const orderedMatch = trimmed.match(/^(\d+)\.\s+(.*)$/)
    if (orderedMatch) {
      flushParagraph()
      flushList()
      orderedItems.push(orderedMatch[2])
      return
    }

    if (orderedItems.length) {
      flushOrderedList()
    }

    if (isTableRow(trimmed)) {
      flushParagraph()
      flushList()
      tableLines.push(trimmed)
      return
    }

    if (tableLines.length) {
      flushTable()
    }

    if (!trimmed) {
      flushParagraph()
      flushList()
      return
    }

    if (/^#{1,6}\s+/.test(trimmed)) {
      flushParagraph()
      flushList()
      const count = (trimmed.match(/^#+/) || [''])[0].length
      const level = Math.min(count, 6)
      parts.push(`<h${level}>${renderInlineMarkdown(trimmed.replace(/^#+\s*/, ''))}</h${level}>`)
      return
    }

    if (/^[-*]\s+/.test(trimmed)) {
      flushParagraph()
      listItems.push(trimmed.replace(/^[-*]\s+/, ''))
      return
    }

    paragraph.push(trimmed)
  })

  flushParagraph()
  flushList()
  flushOrderedList()
  flushTable()
  flushQuote()
  flushCode()
  return parts.length ? parts.join('') : `<p>${renderInlineMarkdown(value)}</p>`
}

async function requestJson(url, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    'X-Session-Id': activeConversationId.value,
    ...(options.headers || {}),
  }
  if (authToken.value) {
    headers.Authorization = `Bearer ${authToken.value}`
  }

  const res = await fetch(url, { ...options, headers })
  const contentType = res.headers.get('content-type') || ''
  const data = contentType.includes('application/json') ? await res.json() : await res.text()

  if (!res.ok) {
    const message = typeof data === 'string' ? data : data?.message || `请求失败：${res.status}`
    const error = new Error(message)
    error.status = res.status
    error.payload = data
    throw error
  }

  return data
}

async function loadConversations() {
  try {
    const items = await requestJson('/api/conversations', { method: 'GET' })
    const seen = new Set()
    conversations.value = (items || []).filter((item) => {
      if (!item?.sessionId || seen.has(item.sessionId)) return false
      seen.add(item.sessionId)
      return true
    })
    if (!conversations.value.some((item) => item.sessionId === activeConversationId.value) && conversations.value[0]?.sessionId) {
      activeConversationId.value = conversations.value[0].sessionId
    }
  } catch (error) {
    console.error(error)
  }
}

async function loadHistory() {
  try {
    chatMessages.value = await requestJson(`/api/conversations/messages?sessionId=${encodeURIComponent(activeConversationId.value)}`, {
      method: 'GET',
    })
  } catch (error) {
    console.error(error)
  }
}

async function loadSessionState() {
  try {
    Object.assign(sessionState, await requestJson(`/api/workspace/session-state?sessionId=${encodeURIComponent(activeConversationId.value)}`, { method: 'GET' }))
  } catch (error) {
    console.error(error)
  }
}

async function refreshConversationState() {
  await Promise.all([loadConversations(), loadHistory(), loadSessionState(), loadSkills()])
  await nextTick()
  scrollToBottom()
  updateScrollToBottomVisibility()
}

function scrollToBottom() {
  window.requestAnimationFrame(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
    }
    showScrollToBottomButton.value = false
  })
}

function updateScrollToBottomVisibility() {
  const el = chatScrollRef.value
  if (!el) return
  const threshold = 24
  const canScroll = el.scrollHeight > el.clientHeight + 4
  const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  const isAwayFromBottom = distanceToBottom > threshold
  showScrollToBottomButton.value = canScroll && isAwayFromBottom
}

function handleChatScroll() {
  updateScrollToBottomVisibility()
}

function appendPendingMessage(content) {
  const now = new Date().toISOString()
  chatMessages.value.push({
    id: `pending-user-${Date.now()}`,
    role: 'user',
    content,
    createdAt: now,
  })
  pendingUserMessage.value = content
  pendingAgentMessage.value = true
  scrollToBottom()
}

function clearPendingMessage() {
  pendingUserMessage.value = ''
  pendingAgentMessage.value = false
}

function selectConversation(id) {
  closeConversationMenu()
  persistSessionId(id)
  refreshConversationState().catch((error) => console.error(error))
}

function toggleConversationMenu(id) {
  openConversationMenuId.value = openConversationMenuId.value === id ? '' : id
}

function closeConversationMenu() {
  openConversationMenuId.value = ''
}

function handleGlobalClick(event) {
  const target = event.target
  if (!(target instanceof Element)) return
  if (target.closest('.conversation-actions') || target.closest('.conversation-menu')) return
  if (!target.closest('.tool-panel') && !target.closest('.tool-row-tooltip')) {
    closeToolDropdown()
  }
  closeConversationMenu()
}

function promptRenameConversation(sessionId) {
  const target = conversations.value.find((item) => item.sessionId === sessionId)
  if (!target) return
  const title = window.prompt('请输入新的会话名称', target.title || '')
  closeConversationMenu()
  if (!title) return
  requestJson('/api/conversations/rename', {
    method: 'POST',
    body: JSON.stringify({ sessionId, title }),
  }).then(async () => {
    await loadConversations()
    showNotice('success', '会话名称已更新')
  }).catch((error) => {
    console.error(error)
    showNotice('error', error.message)
  })
}

async function confirmDeleteConversation(sessionId) {
  closeConversationMenu()
  const ok = window.confirm('确定要删除这个历史对话吗？删除后无法恢复。')
  if (!ok) return
  await deleteConversation(sessionId)
}

watch(activeConversationId, () => {
  nextTick().then(() => {
    scrollToBottom()
    updateScrollToBottomVisibility()
  })
})

watch(chatMessages, async () => {
  await nextTick()
  updateScrollToBottomVisibility()
}, { deep: true })

function groupMessages(messages) {
  const grouped = []
  let last = null
  messages.forEach((item) => {
    if (!last || last.role !== item.role) {
      last = { role: item.role, items: [item] }
      grouped.push(last)
    } else {
      last.items.push(item)
    }
  })
  return grouped
}

async function copyMessageContent(content) {
  if (!content) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(content)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = content
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    showCopyToast('已复制到剪切板')
  } catch (error) {
    console.error(error)
    showNotice('error', '复制失败，请检查浏览器权限。')
  }
}

function groupByDate(messages) {
  const groups = []
  messages.forEach((item) => {
    const label = createTimeDividerKey(item.createdAt || Date.now())
    let group = groups.find((entry) => entry.label === label)
    if (!group) {
      group = { label, items: [] }
      groups.push(group)
    }
    group.items.push(item)
  })
  return groups
}

function createTimeDividerKey(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}

async function createConversation() {
  try {
    const newId = createSessionId()
    const created = await requestJson('/api/conversations', {
      method: 'POST',
      body: JSON.stringify({ sessionId: newId, title: '新对话' }),
    })
    persistSessionId(created.sessionId || newId)
    await refreshConversationState()
    showNotice('success', '已创建新会话')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function renameActiveConversation() {
  if (!activeConversation.value) return
  const title = window.prompt('请输入新的会话名称', activeConversation.value.title || '')
  if (!title) return
  try {
    await requestJson('/api/conversations/rename', {
      method: 'POST',
      body: JSON.stringify({ sessionId: activeConversation.value.sessionId, title }),
    })
    await loadConversations()
    showNotice('success', '会话名称已更新')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function submitRequest() {
  if (!form.question.trim()) {
    showNotice('warning', '请先输入问题。')
    return
  }
  if (cooldownSeconds.value > 0) {
    showNotice('warning', `请求过于频繁，请在 ${cooldownSeconds.value} 秒后重试。`)
    return
  }
  const question = form.question.trim()
  form.question = ''
  appendPendingMessage(question)
  loading.value = true
  clearNotice()
  try {
    if (!activeConversationId.value) {
      await createConversation()
    }
    const payload = {
      ...submitPayload.value,
      message: question,
      sessionId: activeConversationId.value,
    }
    const result = await requestJson('/api/conversations/chat', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    response.value = result.response || null
    await refreshConversationState()
    showNotice('success', '消息已发送')
  } catch (error) {
    console.error(error)
    if (error.status === 429) {
      cooldownSeconds.value = 3
      showNotice('warning', '当前使用人数较多，请稍后再试。')
      const timer = window.setInterval(() => {
        if (cooldownSeconds.value <= 1) {
          cooldownSeconds.value = 0
          window.clearInterval(timer)
          return
        }
        cooldownSeconds.value -= 1
      }, 1000)
    } else {
      showNotice('error', `发送失败：${error.message}`)
    }
  } finally {
    loading.value = false
    clearPendingMessage()
  }
}

function continueConversation() {
  if (!activeConversationId.value) {
    showNotice('warning', '请先选择或创建一个会话。')
    return
  }
  chatScrollRef.value?.scrollIntoView?.()
  showNotice('info', '继续在下方输入问题即可，当前会话上下文会自动保留。')
}

function resetComposer() {
  form.question = ''
  form.code = ''
  form.language = 'Java'
  form.customLanguage = ''
  form.errorMessage = ''
  form.knowledgePoint = ''
  form.enableCrossConversationKnowledge = false
  form.crossConversationShareMode = 'BALANCED'
  form.crossConversationShareLimit = 3
  selectedToolIds.value = ['code-complexity-tool', 'error-keyword-tool', 'learning-resource-tool', 'frontend-code-generation-tool', 'frontend-bug-fix-tool', 'frontend-refactor-tool', 'backend-code-generation-tool', 'backend-bug-fix-tool', 'backend-refactor-tool']
  selectedSkillIds.value = []
  toolInfoId.value = ''
  toolDropdownOpen.value = false
  document.removeEventListener('click', handleGlobalClick)
}

async function handleComposerAttachment(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  try {
    const snippets = []
    for (const file of files.slice(0, 5)) {
      const text = await new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result || ''))
        reader.onerror = () => reject(new Error(`读取附件失败：${file.name}`))
        reader.readAsText(file, 'utf-8')
      })
      snippets.push(`【附件：${file.name}】\n${text.slice(0, 4000)}`)
    }
    const merged = snippets.join('\n\n')
    if (activeMode.value === 'tool') {
      form.code = [form.code, merged].filter(Boolean).join('\n\n')
    } else {
      form.question = [form.question, merged].filter(Boolean).join('\n\n')
    }
    showNotice('success', `已添加 ${files.length} 个附件内容`)
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  } finally {
    event.target.value = ''
  }
}

async function deleteConversation(sessionId) {
  try {
    await requestJson(`/api/conversations?sessionId=${encodeURIComponent(sessionId)}`, { method: 'DELETE' })
    await loadConversations()
    if (activeConversationId.value === sessionId) {
      if (conversations.value[0]?.sessionId) {
        persistSessionId(conversations.value[0].sessionId)
      } else {
        await createConversation()
      }
      await refreshConversationState()
    }
    showNotice('success', '会话已删除')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

function toggleAuthMode() {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
}

async function submitAuth() {
  if (!authForm.username.trim() || !authForm.password.trim() || (authMode.value === 'register' && !authForm.nickname.trim())) {
    showNotice('warning', '请完整填写登录/注册信息。')
    return
  }
  authLoading.value = true
  clearNotice()
  try {
    const payload = authMode.value === 'login'
      ? { username: authForm.username, password: authForm.password }
      : { username: authForm.username, password: authForm.password, nickname: authForm.nickname }
    const data = await requestJson(authMode.value === 'login' ? '/api/auth/login' : '/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    persistAuth(data.token, data.user)
    authForm.username = ''
    authForm.password = ''
    authForm.nickname = ''
    closeAuthModal()
    showNotice('success', `${data.user.nickname}，欢迎回来。`)
    await refreshConversationState()
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  } finally {
    authLoading.value = false
  }
}

async function loadKnowledgeDocuments() {
  if (!currentUser.value) {
    knowledgeDocuments.value = []
    return
  }
  try {
    knowledgeDocuments.value = await requestJson('/api/knowledge')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function loadSkills() {
  if (!currentUser.value) {
    skills.value = []
    selectedSkillIds.value = []
    return
  }
  try {
    skills.value = await requestJson('/api/skills')
    selectedSkillIds.value = selectedSkillIds.value.filter((id) => skills.value.some((skill) => skill.id === id && skill.enabled))
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

function formatScore(value) {
  if (value == null || Number.isNaN(Number(value))) return '0.00'
  return Number(value).toFixed(2)
}

function resetSkillForm() {
  skillEditingId.value = null
  skillForm.name = ''
  skillForm.description = ''
  skillForm.content = ''
  skillForm.enabled = true
  skillForm.contentMode = 'manual'
  skillForm.fileName = ''
}

function resetKnowledgeForm() {
  knowledgeEditingId.value = null
  knowledgeForm.title = ''
  knowledgeForm.sourceName = ''
  knowledgeForm.sourceType = ''
  knowledgeForm.tags = ''
  knowledgeForm.summary = ''
  knowledgeForm.aliasText = ''
  knowledgeForm.categoryText = ''
  knowledgeForm.referenceText = ''
  knowledgeForm.content = ''
  knowledgeForm.enabled = true
}

function editSkill(skill) {
  skillEditingId.value = skill.id
  skillForm.name = skill.name || ''
  skillForm.description = skill.description || ''
  skillForm.content = skill.content || ''
  skillForm.enabled = skill.enabled !== false
  skillForm.contentMode = 'manual'
  skillForm.fileName = ''
}

function handleSkillFileSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const lowerName = file.name.toLowerCase()
  if (!lowerName.endsWith('.md') && !lowerName.endsWith('.markdown') && !lowerName.endsWith('.txt')) {
    showNotice('warning', '只支持 .md / .markdown / .txt 文件。')
    event.target.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    skillForm.content = String(reader.result || '')
    skillForm.fileName = file.name
    skillForm.contentMode = 'file'
    showNotice('success', `已导入 ${file.name}`)
  }
  reader.onerror = () => showNotice('error', '读取 Skill 文件失败。')
  reader.readAsText(file, 'utf-8')
}

function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) return '未知大小'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function normalizeFileExtension(fileName) {
  const matched = String(fileName || '').toLowerCase().match(/\.([a-z0-9]+)$/)
  return matched ? matched[1] : 'txt'
}

function isSupportedKnowledgeFile(file) {
  const extension = normalizeFileExtension(file.name)
  return ['txt', 'md', 'markdown', 'csv', 'json', 'html', 'htm', 'xml', 'log', 'java', 'py', 'js', 'ts', 'tsx', 'jsx', 'vue', 'css', 'scss', 'sql', 'yml', 'yaml', 'properties'].includes(extension)
}

function readFileAsText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error(`读取 ${file.name} 失败`))
    reader.readAsText(file, 'utf-8')
  })
}

function buildKnowledgePayloadFromFile(file, content) {
  const extension = normalizeFileExtension(file.name)
  const title = file.name.replace(/\.[^.]+$/, '') || file.name
  const normalizedContent = content.trim()
  return {
    title,
    sourceName: file.name,
    sourceType: extension,
    tags: `外部导入,${extension}`,
    summary: `从外部文档 ${file.name} 导入，文件大小 ${formatFileSize(file.size)}。`,
    aliases: [title, file.name],
    categories: ['外部文档', extension.toUpperCase()],
    references: [`本地文件：${file.name}`],
    content: normalizedContent,
    enabled: true,
  }
}

async function handleKnowledgeFileImport(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  knowledgeImportLoading.value = true
  knowledgeImportFiles.value = files.map((file) => ({ name: file.name, size: formatFileSize(file.size), status: '等待导入' }))
  let successCount = 0
  let failedCount = 0
  try {
    if (files.length === 1) {
      const file = files[0]
      if (!isSupportedKnowledgeFile(file)) {
        showNotice('warning', `暂不支持 ${file.name} 的格式，请选择文本类文档。`)
        return
      }
      const content = await readFileAsText(file)
      if (!content.trim()) {
        showNotice('warning', `${file.name} 内容为空，无法导入。`)
        return
      }
      const payload = buildKnowledgePayloadFromFile(file, content)
      knowledgeEditingId.value = null
      knowledgeForm.title = payload.title
      knowledgeForm.sourceName = payload.sourceName
      knowledgeForm.sourceType = payload.sourceType
      knowledgeForm.tags = payload.tags
      knowledgeForm.summary = payload.summary
      knowledgeForm.aliasText = payload.aliases.join('\n')
      knowledgeForm.categoryText = payload.categories.join('\n')
      knowledgeForm.referenceText = payload.references.join('\n')
      knowledgeForm.content = payload.content
      knowledgeForm.enabled = true
      knowledgeImportFiles.value = [{ name: file.name, size: formatFileSize(file.size), status: '已填充到编辑区，确认后保存' }]
      showNotice('success', `已读取 ${file.name}，可在编辑区确认后保存。`)
      return
    }

    for (const file of files) {
      const current = knowledgeImportFiles.value.find((item) => item.name === file.name)
      if (!isSupportedKnowledgeFile(file)) {
        failedCount += 1
        if (current) current.status = '格式不支持'
        continue
      }
      const content = await readFileAsText(file)
      if (!content.trim()) {
        failedCount += 1
        if (current) current.status = '内容为空'
        continue
      }
      await requestJson('/api/knowledge', {
        method: 'POST',
        body: JSON.stringify(buildKnowledgePayloadFromFile(file, content)),
      })
      successCount += 1
      if (current) current.status = '已导入知识库'
    }
    await loadKnowledgeDocuments()
    showNotice(successCount ? 'success' : 'warning', `外部文档导入完成：成功 ${successCount} 个，失败 ${failedCount} 个。`)
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  } finally {
    knowledgeImportLoading.value = false
    event.target.value = ''
  }
}

function editKnowledgeDocument(doc) {
  knowledgeEditingId.value = doc.id
  knowledgeForm.title = doc.title || ''
  knowledgeForm.sourceName = doc.sourceName || ''
  knowledgeForm.sourceType = doc.sourceType || ''
  knowledgeForm.tags = doc.tags || ''
  knowledgeForm.summary = doc.summary || ''
  knowledgeForm.aliasText = Array.isArray(doc.aliases) ? doc.aliases.join('\n') : ''
  knowledgeForm.categoryText = Array.isArray(doc.categories) ? doc.categories.join('\n') : ''
  knowledgeForm.referenceText = Array.isArray(doc.references) ? doc.references.join('\n') : ''
  knowledgeForm.content = doc.content || ''
  knowledgeForm.enabled = doc.enabled !== false
}

async function previewKnowledgeDocument(doc) {
  try {
    const data = await requestJson(`/api/knowledge/${doc.id}`, { method: 'GET' })
    editKnowledgeDocument(data)
    activePage.value = 'knowledge'
    showNotice('success', `已加载《${data.title}》到编辑区`)
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function saveKnowledgeDocument() {
  if (!knowledgeForm.title.trim() || !knowledgeForm.content.trim()) {
    showNotice('warning', '知识文档标题和内容不能为空。')
    return
  }
  knowledgeLoading.value = true
  try {
    const payload = {
      title: knowledgeForm.title,
      sourceName: knowledgeForm.sourceName,
      sourceType: knowledgeForm.sourceType,
      tags: knowledgeForm.tags,
      summary: knowledgeForm.summary,
      aliases: knowledgeForm.aliasText.split(/\n|\|/).map((item) => item.trim()).filter(Boolean),
      categories: knowledgeForm.categoryText.split(/\n|\|/).map((item) => item.trim()).filter(Boolean),
      references: knowledgeForm.referenceText.split(/\n|\|/).map((item) => item.trim()).filter(Boolean),
      content: knowledgeForm.content,
      enabled: knowledgeForm.enabled,
    }
    await requestJson(knowledgeEditingId.value ? `/api/knowledge/${knowledgeEditingId.value}` : '/api/knowledge', {
      method: knowledgeEditingId.value ? 'PUT' : 'POST',
      body: JSON.stringify(payload),
    })
    resetKnowledgeForm()
    await loadKnowledgeDocuments()
    showNotice('success', '知识文档已保存')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  } finally {
    knowledgeLoading.value = false
  }
}

async function deleteKnowledgeDocument(id) {
  const ok = window.confirm('确定要删除这个知识文档吗？')
  if (!ok) return
  try {
    await requestJson(`/api/knowledge/${id}`, { method: 'DELETE' })
    await loadKnowledgeDocuments()
    showNotice('success', '知识文档已删除')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function searchKnowledge() {
  if (!knowledgeSearchQuery.value.trim()) {
    showNotice('warning', '请输入要检索的关键词。')
    return
  }
  try {
    knowledgeSearchResult.value = await requestJson(`/api/knowledge/search?query=${encodeURIComponent(knowledgeSearchQuery.value)}&limit=6`, { method: 'GET' })
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function seedKnowledgeSamples() {
  try {
    await requestJson('/api/knowledge/import-samples', { method: 'PATCH' })
    await loadKnowledgeDocuments()
    showNotice('success', '示例知识已导入')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function saveSkill() {
  if (!skillForm.name.trim() || !skillForm.content.trim()) {
    showNotice('warning', 'Skill 名称和内容不能为空。')
    return
  }
  skillLoading.value = true
  try {
    const payload = { name: skillForm.name, description: skillForm.description, content: skillForm.content, enabled: skillForm.enabled }
    await requestJson(skillEditingId.value ? `/api/skills/${skillEditingId.value}` : '/api/skills', {
      method: skillEditingId.value ? 'PUT' : 'POST',
      body: JSON.stringify(payload),
    })
    resetSkillForm()
    await loadSkills()
    showNotice('success', 'Skill 已保存')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  } finally {
    skillLoading.value = false
  }
}

async function deleteSkill(id) {
  const ok = window.confirm('确定要删除这个 Skill 吗？')
  if (!ok) return
  try {
    await requestJson(`/api/skills/${id}`, { method: 'DELETE' })
    selectedSkillIds.value = selectedSkillIds.value.filter((item) => item !== id)
    await loadSkills()
    showNotice('success', 'Skill 已删除')
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  }
}

async function updateProfile() {
  if (!currentUser.value) return
  if (!profileForm.nickname.trim()) {
    showNotice('warning', '昵称不能为空。')
    return
  }
  profileLoading.value = true
  try {
    const user = await requestJson('/api/auth/profile', {
      method: 'POST',
      body: JSON.stringify({ nickname: profileForm.nickname }),
    })
    persistAuth(authToken.value, user)
    showNotice('success', '个人信息已更新。')
    await loadConversations()
  } catch (error) {
    console.error(error)
    showNotice('error', error.message)
  } finally {
    profileLoading.value = false
  }
}

async function logout() {
  try {
    await requestJson('/api/auth/logout', { method: 'POST' })
  } catch (error) {
    console.error(error)
  } finally {
    persistAuth('', null)
    showNotice('success', '已退出登录')
    activePage.value = 'workspace'
  }
}

onMounted(async () => {
  document.addEventListener('click', handleGlobalClick)
  document.addEventListener('click', handleToolOutsideClick)
  await Promise.all([loadConversations(), loadHistory(), loadSessionState()])
  if (!conversations.value.length) {
    await createConversation()
  }
  await nextTick()
  scrollToBottom()
  updateScrollToBottomVisibility()
})

onUnmounted(() => {
  document.removeEventListener('click', handleGlobalClick)
  document.removeEventListener('click', handleToolOutsideClick)
})
</script>

<style scoped>
:global(body) {
  margin: 0;
  min-height: 100vh;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  background: #e9eef5;
  color: #1f2d3d;
}

:global(*) { box-sizing: border-box; }
.app-shell { min-height: 100vh; padding: 16px; display: grid; gap: 14px; }
.panel { background: #fff; border: 1px solid #dbe4ee; border-radius: 18px; }
.topbar { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; background: linear-gradient(180deg, #ffffff 0%, #f9fbff 100%); }
.topbar-actions { display: flex; gap: 10px; align-items: center; }
.topbar-actions > button { height: 40px; box-sizing: border-box; }
.account-trigger { display: flex; align-items: center; gap: 8px; border: 1px solid #dbe4ee; background: #fff; border-radius: 999px; padding: 4px 12px 4px 6px; min-width: 130px; }
.avatar-badge, .avatar { width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; font-weight: 700; font-size: 12px; }
.avatar.user, .avatar-badge { background: #d9ecff; color: #1e6ee6; }
.avatar.agent { background: #e8f7ef; color: #0f8b4c; }
.account-copy { display: grid; line-height: 1.05; }
.account-copy strong { font-size: 12px; font-weight: 700; color: #233142; }
.account-copy small { color: #6b7b8c; font-size: 10px; }
.chat-layout { display: grid; grid-template-columns: 300px 1fr; min-height: calc(100vh - 24px); height: auto; overflow: visible; align-items: stretch; position: relative; }
.chat-layout::before { content: ''; position: absolute; top: 0; bottom: 0; left: 300px; width: 1px; background: #e6edf5; pointer-events: none; z-index: 2; }
.chat-sidebar { border-right: none; padding: 16px; display: flex; flex-direction: column; gap: 16px; background: #fbfcfe; min-height: 0; overflow: hidden; }
.sidebar-head, .chat-header, .bubble-meta, .conversation-card-head, .composer-toolbar, .composer-actions { display: flex; justify-content: space-between; gap: 10px; align-items: center; }
.sidebar-summary { padding: 12px 14px; border-radius: 14px; background: #eef5ff; border: 1px solid #d7e6ff; display: grid; gap: 4px; flex: 0 0 auto; }
.sidebar-summary strong { font-size: 12px; color: #356fe0; letter-spacing: 0.08em; text-transform: uppercase; }
.sidebar-summary p { margin: 0; font-weight: 600; color: #1f2d3d; }
.sidebar-summary small { color: #6b7b8c; }
.conversation-list { display: grid; gap: 10px; overflow: auto; flex: 1; min-height: 0; align-content: start; padding-right: 4px; }
.conversation-card { position: relative; text-align: left; width: 100%; padding: 12px; padding-right: 48px; border-radius: 14px; border: 1px solid #e1e8f0; background: #fff; cursor: pointer; flex: 0 0 auto; overflow: hidden; }
.conversation-card.active { border-color: #4c8bf5; background: #f2f7ff; }
.conversation-card p { margin: 8px 0 6px; color: #637282; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; text-overflow: ellipsis; }
.conversation-card small, .chat-meta { color: #8291a3; }
.conversation-actions { position: absolute; right: 3px; bottom: 3px; width: 34px; height: 34px; display: grid; place-items: center; z-index: 2; }
.conversation-menu-trigger { width: 30px; height: 30px; border-radius: 999px; border: 1px solid #dbe4ee; background: #fff; color: #5f7082; font-size: 16px; line-height: 1; opacity: 1; padding: 0}
.conversation-menu { position: absolute; right: 0; bottom: 36px; min-width: 120px; padding: 6px; border: 1px solid #dbe4ee; border-radius: 12px; background: #fff; box-shadow: 0 8px 24px rgba(31, 45, 61, 0.12); display: grid; gap: 4px; z-index: 10; }
.conversation-menu button { width: 100%; border-radius: 10px; padding: 8px 10px; background: #f7f9fc; color: #233142; text-align: left; }
.conversation-menu button:hover { background: #eef5ff; }
.conversation-menu .danger { color: #c23939; background: #fff1f1; }
.conversation-menu .danger:hover { background: #ffe7e7; }
.chat-panel { display: flex; flex-direction: column; min-height: 0; height: auto; overflow: visible; position: relative; }
.chat-messages-shell { position: relative; height: 100vh; min-height: 100vh; max-height: 100vh; flex: 0 0 100vh; }
.chat-messages { padding: 18px; overflow-y: auto; overflow-x: hidden; display: grid; gap: 16px; background: #f5f8fc; position: relative; align-content: start; height: 100%; min-height: 100%; max-height: 100%; }
.scroll-bottom-button { position: absolute; right: 18px; bottom: 20px; width: 44px; height: 44px; border-radius: 50%; background: #fff; border: 1px solid #dbe4ee; box-shadow: 0 6px 18px rgba(31, 45, 61, 0.12); display: grid; place-items: center; z-index: 60; pointer-events: auto; }
.scroll-bottom-button:hover { border-color: #bcd2ee; transform: translateY(-1px); }
.scroll-bottom-icon { font-size: 20px; line-height: 1; color: #233142; transform: translateY(-1px); }
.chat-composer { padding: 10px 18px 14px; border-top: 1px solid #e6edf5; background: #fff; flex: 0 0 auto; }
.composer-card { border: 1px solid #e1e8f0; border-radius: 18px; padding: 12px; background: #fbfcfe; }
.composer-form { display: grid; gap: 10px; }
textarea { resize: vertical; min-height: 64px; }
.chat-row { display: flex; width: 100%; }
.chat-row.user { justify-content: flex-end; }
.chat-row.agent { justify-content: flex-start; }
.message-line { position: relative; display: flex; align-items: flex-start; width: 100%; max-width: min(780px, 100%); padding-bottom: 20px; overflow: visible; }
.message-line.user { justify-content: flex-end; }
.message-line.agent { justify-content: flex-start; }
.bubble-wrap { display: grid; gap: 6px; width: min(100%, fit-content); max-width: min(780px, 100%); }
.chat-row.user .bubble-wrap { align-items: flex-end; }
.chat-row.agent .bubble-wrap { align-items: flex-start; }
.bubble-time { font-size: 12px; color: #8b96a3; text-align: center; }
.bubble { width: 100%; max-width: 100%; min-width: 0; box-sizing: border-box; padding: 14px 16px; border-radius: 16px; background: #fff; border: 1px solid #dde5ee; line-height: 1.8; overflow-wrap: anywhere; }
.chat-row.user .bubble { background: #dcf4ff; border-color: #b7def0; }
.copy-trigger { position: absolute; bottom: -10px; width: 28px; height: 28px; border-radius: 8px; border: 1px solid #dbe4ee; background: rgba(255, 255, 255, 0.96); display: grid; place-items: center; opacity: 0; pointer-events: none; transition: opacity 0.18s ease, transform 0.18s ease, background 0.18s ease; z-index: 8; font-size: 14px; line-height: 1; color: #6b7b8c; padding: 0; }
.message-line.user .copy-trigger { right: 0px; }
.message-line.agent .copy-trigger { left: -34px; }
.chat-row:hover .copy-trigger { opacity: 1; pointer-events: auto; transform: translateY(-1px); }
.copy-trigger:hover { background: #f0f6ff; }
.copy-trigger.disabled { opacity: 0; pointer-events: none; }
.thinking-bubble { display: flex; align-items: center; justify-content: center; min-height: 52px; }
.copy-toast { position: fixed; left: 50%; top: 20px; transform: translateX(-50%) translateY(-12px); background: rgba(18, 28, 42, 0.92); color: #fff; padding: 10px 14px; border-radius: 999px; font-size: 13px; letter-spacing: 0.02em; box-shadow: 0 10px 24px rgba(15, 23, 32, 0.24); opacity: 0; pointer-events: none; transition: opacity 0.2s ease, transform 0.2s ease; z-index: 50; }
.copy-toast.visible { opacity: 1; transform: translateX(-50%) translateY(0); }
.thinking-dots { display: inline-flex; gap: 6px; align-items: center; }
.thinking-dots i { width: 8px; height: 8px; border-radius: 50%; background: #8b96a3; animation: thinkingPulse 1.2s infinite ease-in-out; }
.thinking-dots i:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots i:nth-child(3) { animation-delay: 0.4s; }
.bubble :deep(p) { margin: 0 0 8px; }
.bubble :deep(p:last-child) { margin-bottom: 0; }
.bubble :deep(h1), .bubble :deep(h2), .bubble :deep(h3), .bubble :deep(h4), .bubble :deep(h5), .bubble :deep(h6) { margin: 4px 0 8px; line-height: 1.35; color: #16324f; }
.bubble :deep(h1) { font-size: 22px; }
.bubble :deep(h2) { font-size: 20px; }
.bubble :deep(h3) { font-size: 18px; }
.bubble :deep(blockquote) { margin: 8px 0 0; padding: 10px 14px; border-left: 3px solid #cfe0f5; background: #f8fbff; color: #415165; border-radius: 0 12px 12px 0; }
.bubble :deep(blockquote p) { margin: 0; }
.bubble :deep(ol) { margin: 8px 0 0; padding-left: 18px; }
.bubble :deep(ol li) { margin: 4px 0; }
.bubble :deep(hr) { margin: 12px 0 0; border: 0; border-top: 1px solid #dbe4ee; }
.bubble :deep(pre) { margin: 8px 0 0; padding: 12px; border-radius: 12px; overflow: auto; background: #0f1720; color: #e8eef5; }
.bubble :deep(code) { font-family: Menlo, Monaco, monospace; }
.bubble :deep(ul) { margin: 8px 0 0; padding-left: 18px; }
.bubble :deep(.table-wrap) { width: 100%; max-width: 100%; overflow-x: auto; overflow-y: hidden; margin: 8px 0 0; border: 1px solid #dbe4ee; border-radius: 12px; background: #fff; }
.bubble :deep(table) { width: max-content; min-width: 100%; border-collapse: collapse; }
.bubble :deep(th), .bubble :deep(td) { padding: 10px 12px; border-bottom: 1px solid #e6edf5; text-align: left; vertical-align: top; }
.bubble :deep(th) { background: #f5f8fc; color: #233142; }
.bubble :deep(tr:last-child td) { border-bottom: none; }
.chat-composer { padding: 8px 18px 12px; border-top: none; background: #f7fbff; flex: 0 0 auto; }
.composer-card { border: none; border-radius: 0; padding: 0; background: transparent; }
.composer-form { display: grid; gap: 10px; }
.composer-input-shell { border: none; border-radius: 0; background: transparent; padding: 0; display: grid; gap: 8px; }
.composer-input-shell textarea { border: 1px solid #d8e1ea; border-radius: 14px; background: #fff; min-height: 140px; max-height: 280px; resize: none; overflow-y: auto; box-shadow: 0 6px 18px rgba(31, 45, 61, 0.08); padding: 14px 16px; line-height: 1.5; transition: border-color 0.2s ease, box-shadow 0.2s ease; }
.composer-input-shell textarea::placeholder { color: #9aa3ad; }
.composer-input-shell textarea:focus { outline: none; border-color: #c5d2e0; box-shadow: 0 8px 22px rgba(31, 45, 61, 0.12); }
.composer-input-footer { display: flex; justify-content: space-between; align-items: flex-end; gap: 10px; }
.composer-left-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.composer-right-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.hidden-file-input { display: none; }
.composer-icon-button { width: 38px; height: 38px; border-radius: 999px; border: 1px solid #d8e3ef; background: #fff; color: #4a5b70; display: grid; place-items: center; cursor: pointer; font-size: 18px; padding: 0; line-height: 1; }
.composer-icon-button.send { background: linear-gradient(180deg, #2f7cf6 0%, #1e6ee6 100%); color: #fff; border-color: #2b71e0; font-weight: 700; box-shadow: 0 6px 14px rgba(47, 124, 246, 0.3); }
.composer-icon-button.send:disabled { background: linear-gradient(180deg, #b6c9ff 0%, #9fb8ff 100%); border-color: #a6bcff; box-shadow: none; }
.composer-icon-button:disabled { opacity: 1; cursor: not-allowed; }
.composer-toolbar { display: none; }
.structured-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.structured-grid label { display: grid; gap: 6px; }
.structured-grid .code-field { grid-column: 1 / -1; }
.structured-grid .code-field textarea { min-height: 180px; font-family: Menlo, Monaco, monospace; }
.composer-actions { justify-content: flex-start; flex-wrap: wrap; }
.composer-toolbar .ghost,
.cross-share-toggle,
.share-mode-select,
.share-limit-select,
.session-pill { height: 38px; box-sizing: border-box; border-radius: 999px; }
.composer-toolbar .ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  font-size: 13px;
  border: 1px solid #dbe4ee;
}
.cross-share-controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cross-share-toggle {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
  background: #f2f7ff;
  color: #356fe0;
  font-size: 13px;
  border: 1px solid #cfe0f5;
}
.cross-share-toggle input { width: 14px; height: 14px; accent-color: #2f7cf6; margin: 0; }
.share-mode-select, .share-limit-select {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: #f8fbff;
  border: 1px solid #dbe4ee;
  font-size: 12px;
  color: #5f7082;
}
.share-mode-select select {
  width: auto;
  min-width: 112px;
  height: 28px;
  padding: 0 8px;
  border-radius: 10px;
  border: 1px solid #dbe4ee;
  background: #fff;
}
.share-limit-select input[type="range"] { width: 92px; margin: 0; }
.share-limit-select small { color: #356fe0; font-weight: 600; min-width: 30px; text-align: right; }
.session-pill {
  display: inline-flex;
  align-items: center;
  padding: 0 13px;
  background: #eef5ff;
  color: #356fe0;
  font-size: 13px;
  border: 1px solid #dbe4ee;
}
textarea, input, select { width: 100%; border: 1px solid #dbe4ee; border-radius: 12px; padding: 12px 14px; font-size: 14px; background: #fff; color: #1f2d3d; }
textarea { resize: vertical; min-height: 96px; }
button { border: none; border-radius: 999px; padding: 10px 14px; cursor: pointer; }
.primary { background: #2f7cf6; color: #fff; }
.ghost { background: #eef3f9; color: #233142; }
.active { background: #d9ecff; color: #1e6ee6; }
.small-button { padding: 8px 12px; }
.notice-toast { position: fixed; top: 20px; left: 50%; transform: translateX(-50%) translateY(-14px); min-width: 280px; max-width: min(560px, calc(100vw - 32px)); display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border-radius: 16px; box-shadow: 0 14px 36px rgba(15, 23, 32, 0.18); z-index: 120; opacity: 0; pointer-events: none; transition: opacity 0.16s ease, transform 0.16s ease; }
.notice-toast.visible { opacity: 1; transform: translateX(-50%) translateY(0); pointer-events: auto; }
.notice-toast.success { background: #e7f7ef; color: #0f8b4c; }
.notice-toast.warning { background: #fff6df; color: #b57400; }
.notice-toast.error { background: #ffe7e7; color: #cc3a3a; }
.notice-toast.info { background: #eaf3ff; color: #356fe0; }
.notice-toast-close { width: 26px; height: 26px; min-width: 26px; border-radius: 999px; background: rgba(255,255,255,0.75); color: currentColor; display: grid; place-items: center; padding: 0; line-height: 1; font-size: 16px; }
.empty-state { padding: 24px; border: 1px dashed #dbe4ee; border-radius: 16px; color: #6b7b8c; background: #fff; }
.empty-state.compact { padding: 18px; }
.auth-form { display: grid; gap: 12px; }
.profile-page { padding: 28px; background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%); }
.profile-hero { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 22px; padding-bottom: 18px; border-bottom: 1px solid #e6edf5; }
.profile-hero h2 { margin: 0; font-size: 28px; }
.profile-hero-copy { margin: 6px 0 0; color: #6b7b8c; }
.profile-layout { display: grid; gap: 18px; }
.profile-summary { padding: 22px; display: grid; gap: 14px; border: 1px solid #dbe4ee; background: linear-gradient(135deg, #f7fbff 0%, #ffffff 100%); }
.profile-summary-top { display: flex; align-items: center; gap: 14px; }
.profile-avatar { width: 60px; height: 60px; border-radius: 18px; display: grid; place-items: center; background: linear-gradient(135deg, #d9ecff, #eef5ff); color: #1e6ee6; font-weight: 700; font-size: 22px; }
.profile-summary h3 { margin: 0; font-size: 20px; }
.profile-summary p { margin: 4px 0 0; color: #6b7b8c; }
.profile-badges { display: flex; gap: 10px; flex-wrap: wrap; }
.profile-badges span { padding: 7px 11px; border-radius: 999px; background: #eef5ff; color: #356fe0; font-size: 12px; }
.profile-grid, .skill-page-grid { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 16px; align-items: start; }
.skill-content-tabs { display: flex; gap: 8px; }
.skill-content-tabs .ghost { padding: 8px 12px; }
.skill-file-picker { display: grid; gap: 6px; }
.skill-file-picker input[type="file"] { padding: 10px 0; border: none; }
.skill-file-picker small { color: #6b7b8c; }
.skill-enabled { display: inline-flex; align-items: center; gap: 8px; }
.skill-enabled input { width: 16px; height: 16px; accent-color: #2f7cf6; }
.skill-list-item { display: flex; justify-content: space-between; gap: 12px; padding: 14px 0; border-top: 1px solid #e6edf5; }
.skill-list-item:first-of-type { border-top: none; }
.skill-list-item p { margin: 4px 0 0; color: #5f7082; }
.skill-list-item small { color: #8b96a3; }
.skill-list-actions { display: flex; gap: 8px; align-items: center; }
.profile-grid { grid-template-columns: minmax(0, 1fr) 280px; }
.profile-card { padding: 20px; border: 1px solid #dbe4ee; background: #fff; }
.profile-card h3 { margin-top: 0; margin-bottom: 16px; font-size: 18px; }
.profile-card label span { font-size: 13px; color: #5f7082; }
.skill-picker { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 10px 12px; border: 1px solid #dbe4ee; border-radius: 16px; background: #fff; }
.skill-choice { display: inline-flex; align-items: center; gap: 6px; padding: 6px 10px; border-radius: 999px; background: #f2f7ff; color: #356fe0; }
.skill-choice input, .skill-enabled input { width: 14px; height: 14px; margin: 0; }
.skill-list-item { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid #e6edf5; }
.skill-list-item p { margin: 4px 0; color: #6b7b8c; }
.skill-list-item small { color: #8291a3; }
.skill-list-actions { display: flex; gap: 8px; align-items: center; }
.skill-enabled { display: inline-flex; align-items: center; gap: 8px; color: #5f7082; }
.profile-actions { display: flex; justify-content: flex-start; align-items: center; min-height: 100px; }
.danger-button { background: #fff1f1; color: #c23939; border: 1px solid #f0c7c7; }
.profile-empty { min-height: 260px; display: grid; place-items: center; text-align: center; padding: 24px; background: linear-gradient(180deg, #ffffff, #f8fbff); border: 1px dashed #dbe4ee; border-radius: 18px; }
.modal-mask { position: fixed; inset: 0; background: rgba(15, 23, 32, 0.45); display: grid; place-items: center; padding: 18px; }
.modal-card { width: min(420px, 100%); padding: 18px; }
@media (max-width: 640px) { .profile-page { padding: 18px; } .profile-hero, .topbar, .chat-header, .profile-hero { flex-direction: column; align-items: stretch; } .structured-grid { grid-template-columns: 1fr; } .profile-grid { grid-template-columns: 1fr; } .profile-hero { align-items: flex-start; } .chat-row.user, .chat-row.agent { justify-content: flex-start; } .bubble-wrap { width: 100%; max-width: 100%; } .message-line { max-width: 100%; width: 100%; padding-bottom: 16px; } .message-line.user .copy-trigger { right: 8px; left: auto; } .message-line.agent .copy-trigger { left: 8px; right: auto; } .conversation-actions { right: 6px; bottom: 6px; } .conversation-menu { right: 0; bottom: 36px; }
}

@keyframes thinkingPulse {
  0%, 80%, 100% { transform: scale(0.65); opacity: 0.35; }
  40% { transform: scale(1); opacity: 1; }
}
.eyebrow { margin: 0 0 6px; font-size: 12px; letter-spacing: 0.2em; text-transform: uppercase; color: #2f7cf6; }
.small { font-size: 11px; }
.section-title { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.tool-panel { position: relative; }
.tool-panel-trigger { width: 100%; display: flex; justify-content: space-between; align-items: center; gap: 12px; border-radius: 16px; border: 1px solid #cfe0f5; background: linear-gradient(135deg, #ffffff 0%, #f5f9ff 100%); color: #233142; padding: 12px 14px; text-align: left; box-shadow: 0 6px 16px rgba(47, 124, 246, 0.08); }
.tool-trigger-copy { display: grid; gap: 2px; }
.tool-trigger-copy strong { font-size: 14px; color: #16324f; }
.tool-panel-trigger small { color: #6b7b8c; font-size: 12px; }
.tool-trigger-chevron { width: 26px; height: 26px; display: grid; place-items: center; border-radius: 50%; background: #eef5ff; color: #356fe0; font-size: 16px; }
.tool-dropdown { position: absolute; left: 0; top: calc(100% + 8px); width: min(520px, 100%); max-height: 320px; overflow-y: auto; overflow-x: hidden; padding: 10px; border: 1px solid #cfe0f5; border-radius: 16px; background: rgba(255,255,255,0.98); box-shadow: 0 14px 36px rgba(31,45,61,0.16); display: grid; gap: 10px; z-index: 30; backdrop-filter: blur(10px); scrollbar-width: thin; scrollbar-color: #9fc2f5 #eef5ff; }
.tool-dropdown::-webkit-scrollbar { width: 10px; }
.tool-dropdown::-webkit-scrollbar-track { background: #eef5ff; border-radius: 999px; }
.tool-dropdown::-webkit-scrollbar-thumb { background: linear-gradient(180deg, #9fc2f5 0%, #6fa3f0 100%); border-radius: 999px; border: 2px solid #eef5ff; }
.tool-dropdown::-webkit-scrollbar-thumb:hover { background: linear-gradient(180deg, #86b3f3 0%, #5a94ea 100%); }
.tool-group-block { display: grid; gap: 8px; }
.tool-group-title { font-size: 12px; font-weight: 700; color: #356fe0; letter-spacing: 0.04em; padding: 0 2px; }
.tool-dropdown-item { position: relative; display: flex; flex-direction: row; align-items: center; gap: 10px; min-height: 40px; padding: 8px 10px; border-radius: 12px; color: #233142; transition: background 0.16s ease, transform 0.16s ease; }
.tool-dropdown-item:hover { background: #f0f6ff; transform: translateY(-1px); }
.tool-dropdown-item input { width: 16px; height: 16px; flex: 0 0 auto; margin: 0; padding: 0; accent-color: #2f7cf6; }
.tool-dropdown-item > span { flex: 1; min-width: 0; font-weight: 600; }
.tool-mini-info { width: 18px; height: 18px; min-width: 18px; min-height: 18px; border-radius: 50%; border: 1px solid #cfe0f5; background: #fff; color: #356fe0; font-weight: 700; padding: 0; font-size: 10px; line-height: 1; display: grid; place-items: center; flex: 0 0 auto; box-shadow: 0 3px 8px rgba(47,124,246,0.1); }
.tool-mini-info:hover { background: #f7fbff; border-color: #9fc2f5; }
.tool-row-tooltip { position: absolute; left: calc(100% + 10px); top: 50%; transform: translateY(-50%); width: 250px; margin: 0; padding: 11px 13px; border-radius: 14px; border: 1px solid #cfe0f5; background: #fff; color: #415165; box-shadow: 0 14px 32px rgba(31,45,61,0.16); z-index: 40; }
.tool-row-tooltip::before { content: ''; position: absolute; left: -6px; top: 50%; width: 10px; height: 10px; background: #fff; border-left: 1px solid #cfe0f5; border-bottom: 1px solid #cfe0f5; transform: translateY(-50%) rotate(45deg); }
.tool-row-tooltip strong { color: #16324f; }
.tool-row-tooltip p { margin: 5px 0 0; line-height: 1.55; color: #5f7082; }
.tool-code-field { padding: 12px; border: 1px solid #e1e8f0; border-radius: 16px; background: #fff; }
.tool-code-field span { font-size: 13px; color: #5f7082; }
.tool-code-field textarea { min-height: 150px; font-family: Menlo, Monaco, monospace; }
.tool-meta-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; padding: 12px; border: 1px solid #e1e8f0; border-radius: 16px; background: #fff; }
.tool-meta-grid label { display: grid; gap: 6px; }
.tool-meta-grid label span { font-size: 13px; color: #5f7082; }
.conversation-card:hover, .ghost:hover, .primary:hover { opacity: 0.95; }
@media (max-width: 960px) { .chat-layout { grid-template-columns: 1fr; height: auto; min-height: calc(100vh - 24px); } .chat-sidebar { border-right: none; border-bottom: 1px solid #e6edf5; max-height: 320px; } .conversation-list { max-height: 220px; } .sidebar-summary { display: none; } .chat-messages-shell { height: 58vh; min-height: 58vh; max-height: 58vh; } .chat-messages { min-height: 100%; } .scroll-bottom-button { right: 14px; bottom: 20px; } .message-line.user .copy-trigger { right: 6px; left: auto; } .message-line.agent .copy-trigger { left: 6px; right: auto; } .conversation-actions { right: 6px; bottom: 6px; } .conversation-menu { right: 0; bottom: 36px; } .conversation-menu-trigger { opacity: 1; border-color: #dbe4ee; background: #fff; }
}
@media (max-width: 640px) { .topbar, .chat-header, .profile-hero { flex-direction: column; align-items: stretch; } .structured-grid { grid-template-columns: 1fr; } .profile-grid { grid-template-columns: 1fr; } }
</style>
