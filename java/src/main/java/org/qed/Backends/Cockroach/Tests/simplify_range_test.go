package rulescript_test

import "testing"

func TestSimplifyRange(t *testing.T) {
	rule := loadRule(t, "SimplifyRange")

	t.Run("removes_true_conjunct", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("t", 1)
		condition := e.f.ConstructAnd(e.v(cols[0]), e.f.ConstructTrue())
		before := e.f.ConstructSelect(scan, e.filters(condition))
		want := e.f.ConstructSelect(scan, e.filters(e.v(cols[0])))
		e.expectRewrite(rule, before, want)
	})

	t.Run("rejects_non_true_conjunct", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("t", 1)
		condition := e.f.ConstructAnd(e.v(cols[0]), e.f.ConstructFalse())
		plan := e.f.ConstructSelect(scan, e.filters(condition))
		e.expectNoMatch(rule, plan)
	})
}
