package ouccs.smy.paiclilearn.hitl;

/**
 * 可动态切换启用/禁用的 HITL 处理器包装。
 * <p>委托给内部 HitlHandler，提供 setMode 快速切换审批模式。</p>
 *
 * @since s05 [s05 新增]
 */
public class SwitchableHitlHandler implements HitlHandler {
    private volatile HitlHandler delegate;

    public SwitchableHitlHandler(HitlHandler delegate) { this.delegate = delegate; }

    public void setDelegate(HitlHandler delegate) { this.delegate = delegate; }
    public HitlHandler getDelegate() { return delegate; }

    @Override public ApprovalResult requestApproval(ApprovalRequest r) { return delegate.requestApproval(r); }
    @Override public boolean isEnabled() { return delegate.isEnabled(); }
    @Override public void setEnabled(boolean e) { delegate.setEnabled(e); }
    @Override public boolean isApprovedAllForTool(String t) { return delegate.isApprovedAllForTool(t); }
    @Override public void clearApprovedAll() { delegate.clearApprovedAll(); }
}
