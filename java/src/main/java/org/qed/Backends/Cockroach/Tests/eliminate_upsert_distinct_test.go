package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateUpsertDistinct(t *testing.T) {
	rule := loadRule(t, "EliminateUpsertDistinct")
	e := newEnv(t)
	if _, err := e.cat.ExecuteDDL("CREATE TABLE w (b INT)"); err != nil {
		t.Fatal(err)
	}
	s, sc := e.scan("t", 1)
	before := e.f.ConstructGroupBy(s, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{}, opt.MakeColSet(sc[0]))
	e.expectRewrite(rule, before, want)

	wide, wc := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wide, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(wc[0])}))

	nonKey, nc := e.scan("w", 1)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(nonKey, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nc[0])}))

	if _, err := e.cat.ExecuteDDL("CREATE TABLE lk (a INT UNIQUE)"); err != nil {
		t.Fatal(err)
	}
	lax, lc := e.scan("lk", 1)
	laxBefore := e.f.ConstructGroupBy(lax, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	e.expectNoMatch(rule, laxBefore)
}
