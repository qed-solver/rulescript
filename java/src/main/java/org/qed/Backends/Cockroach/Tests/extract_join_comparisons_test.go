package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestExtractJoinComparisons(t *testing.T) {
	rule := loadRule(t, "ExtractJoinComparisons")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	leftExpr := e.f.ConstructPlus(e.v(lc[0]), e.num(1))
	rightExpr := e.f.ConstructPlus(e.v(rc[0]), e.num(2))
	before := e.f.ConstructInnerJoin(l, r,
		e.filters(e.f.ConstructEq(leftExpr, rightExpr)), &memo.JoinPrivate{})

	w := newEnv(t)
	wl, wlc := w.scan("t", 1)
	wr, wrc := w.scan("u", 1)
	fcol := w.f.Metadata().AddColumn("computed", types.Int)
	gcol := w.f.Metadata().AddColumn("computed", types.Int)
	leftProject := w.f.ConstructProject(wl, memo.ProjectionsExpr{
		w.f.ConstructProjectionsItem(w.f.ConstructPlus(w.v(wlc[0]), w.num(1)), fcol),
	}, opt.MakeColSet(wlc[0]))
	rightProject := w.f.ConstructProject(wr, memo.ProjectionsExpr{
		w.f.ConstructProjectionsItem(w.f.ConstructPlus(w.v(wrc[0]), w.num(2)), gcol),
	}, opt.MakeColSet(wrc[0]))
	joined := w.f.ConstructInnerJoin(leftProject, rightProject,
		w.filters(w.f.ConstructEq(w.v(fcol), w.v(gcol))), &memo.JoinPrivate{})
	want := w.f.ConstructProject(joined, nil, opt.MakeColSet(wlc[0], wrc[0]))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r,
		e.filters(e.f.ConstructEq(leftExpr, rightExpr)), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r,
		e.filters(e.f.ConstructEq(leftExpr, rightExpr), e.f.ConstructGt(e.v(lc[0]), e.num(0))),
		&memo.JoinPrivate{}))
}

func TestSQLExtractJoinComparisons(t *testing.T) {
	sql := "SELECT t.a,u.x FROM t JOIN u ON t.a+1=u.x+2"
	helper := func(r opt.RuleName) bool {
		return r == opt.EliminateProject || strings.HasPrefix(r.String(), "Prune")
	}
	before, _ := planFor(t, sql, helper)
	after, fired := planFor(t, sql, helper, "ExtractJoinComparisons")
	if strings.Join(fired, ",") != "ExtractJoinComparisons" || !strings.Contains(before, "inner-join") ||
		strings.Count(after, "project") < 2 {
		t.Fatalf("comparison extraction did not rewrite SQL plan: %v\nbefore:\n%s\nafter:\n%s", fired, before, after)
	}
}
