package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateAggDistinct(t *testing.T) {
	rule := loadRule(t, "EliminateAggDistinct")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("m", types.Int)
	group := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}
	distinctMin := e.f.ConstructAggDistinct(e.f.ConstructMin(e.v(sc[0])))
	before := e.f.ConstructGroupBy(s, memo.AggregationsExpr{e.f.ConstructAggregationsItem(distinctMin, out)}, group)
	plainMin := e.f.ConstructMin(e.v(sc[0]))
	want := e.f.ConstructGroupBy(s, memo.AggregationsExpr{e.f.ConstructAggregationsItem(plainMin, out)}, group)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, want)
	wide, wc := e.scan("t", 2)
	wideDistinct := e.f.ConstructAggDistinct(e.f.ConstructMin(e.v(wc[0])))
	wideGroup := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(wc[0])}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wide, memo.AggregationsExpr{e.f.ConstructAggregationsItem(wideDistinct, out)}, wideGroup))
}
