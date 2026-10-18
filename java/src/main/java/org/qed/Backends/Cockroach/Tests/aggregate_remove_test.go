package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateRemove(t *testing.T) {
	rule := loadRule(t, "AggregateRemove")
	e := newEnv(t)
	if _, err := e.cat.ExecuteDDL("CREATE TABLE nk (a INT, b INT)"); err != nil {
		t.Fatal(err)
	}
	s, sc := e.scan("u", 1)
	before := e.f.ConstructGroupBy(s, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(sc[0]),
	})
	e.expectRewrite(rule, before, s)

	nk, nc := e.scan("nk", 1)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(nk, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(nc[0]),
	}))

	vc := e.f.Metadata().AddColumn("v", types.Int)
	vals := e.intValues([]opt.ColumnID{vc}, []int{1}, []int{2})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(vals, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(vc),
	}))

	e.expectNoMatch(rule, e.f.ConstructScalarGroupBy(s, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{}))
}
