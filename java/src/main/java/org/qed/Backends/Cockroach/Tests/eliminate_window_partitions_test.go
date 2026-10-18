package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateWindowPartitions(t *testing.T) {
	rule := loadRule(t, "EliminateWindowPartitions")
	e := newEnv(t)

	s, sc := e.scan("t", 2)
	constCol := e.f.Metadata().AddColumn("const", types.Int)
	withConst := e.f.ConstructProject(s,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(0), constCol)},
		opt.MakeColSet(sc[0]))
	cnt := e.f.Metadata().AddColumn("count", types.Int)
	before := e.f.ConstructGroupBy(withConst,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(sc[0])), cnt)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0], constCol)})

	grouped := e.f.ConstructGroupBy(s,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(sc[0])), cnt)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])})
	constCol2 := e.f.Metadata().AddColumn("const", types.Int)
	want := e.f.ConstructProject(grouped,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(0), constCol2)},
		opt.MakeColSet(sc[0], cnt))

	e.expectRewriteUpToFreshColumns(rule, before, want)

	s3, sc3 := e.scan("t", 2)
	nonConstCol := e.f.Metadata().AddColumn("nonconst", types.Int)
	keyed := e.f.ConstructProject(s3,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(sc3[1]), nonConstCol)},
		opt.MakeColSet(sc3[0]))
	cnt3 := e.f.Metadata().AddColumn("count", types.Int)
	nonConst := e.f.ConstructGroupBy(keyed,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(sc3[0])), cnt3)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc3[0], nonConstCol)})
	e.expectNoMatch(rule, nonConst)

	s4, sc4 := e.scan("t", 2)
	cnt4 := e.f.Metadata().AddColumn("count", types.Int)
	twoKeys := e.f.ConstructGroupBy(s4,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(sc4[0])), cnt4)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc4[0], sc4[1])})
	e.expectNoMatch(rule, twoKeys)
}
