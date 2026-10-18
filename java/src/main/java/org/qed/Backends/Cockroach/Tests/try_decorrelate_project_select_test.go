package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateProjectSelect(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateProjectSelect")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("t", 2)
	filter := testProjectSelectPred(e, "select_filter", e.v(rc[0]))
	selected := e.f.ConstructSelect(r, e.filters(filter))
	right := e.f.ConstructProject(selected, memo.EmptyProjectionsExpr, opt.MakeColSet(rc[1]))
	on := e.filters(testProjectSelectPred(e, "on_cond", e.v(lc[0]), e.v(rc[1])))
	before := e.f.ConstructLeftJoin(l, right, on, &memo.JoinPrivate{})

	pushed := e.f.ConstructProject(r, memo.EmptyProjectionsExpr, opt.MakeColSet(rc[0], rc[1]))
	merged := e.filters(
		testProjectSelectPred(e, "on_cond", e.v(lc[0]), e.v(rc[1])),
		testProjectSelectPred(e, "select_filter", e.v(rc[0])),
	)
	joined := e.f.ConstructLeftJoin(l, pushed, merged, &memo.JoinPrivate{})
	want := e.f.ConstructProject(joined, memo.EmptyProjectionsExpr, opt.MakeColSet(lc[0], rc[1]))
	e.expectRewrite(rule, before, want)

	l, lc = e.scan("t", 1)
	r, rc = e.scan("t", 2)
	filter = testProjectSelectPred(e, "select_filter", e.v(rc[0]))
	selected = e.f.ConstructSelect(r, e.filters(filter))
	right = e.f.ConstructProject(selected, memo.EmptyProjectionsExpr, opt.MakeColSet(rc[1]))
	on = e.filters(testProjectSelectPred(e, "on_cond", e.v(lc[0]), e.v(rc[1])))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, right, on, &memo.JoinPrivate{}))
}

func testProjectSelectPred(e *env, name string, args ...opt.ScalarExpr) opt.ScalarExpr {
	return e.f.ConstructFunction(memo.ScalarListExpr(args), &memo.FunctionPrivate{Name: name, Typ: types.Bool, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{}})
}
