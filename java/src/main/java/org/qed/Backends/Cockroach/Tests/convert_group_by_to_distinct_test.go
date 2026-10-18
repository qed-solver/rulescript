package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestConvertGroupByToDistinct(t *testing.T) {
	rule := loadRule(t, "ConvertGroupByToDistinct")
	e := newEnv(t)

	s, sc := e.scan("nn", 2)
	before := e.f.ConstructGroupBy(s, memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(sc[0], sc[1]),
	})
	dup0 := e.f.Metadata().AddColumn("dup", types.Int)
	dup1 := e.f.Metadata().AddColumn("dup", types.Int)
	out0 := e.f.Metadata().AddColumn("k1", types.Int)
	out1 := e.f.Metadata().AddColumn("k2", types.Int)
	dupSide := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(sc[0]), dup0),
		e.f.ConstructProjectionsItem(e.v(sc[1]), dup1),
	}, opt.ColSet{})
	want := e.f.ConstructIntersect(s, dupSide, &memo.SetPrivate{
		LeftCols:  opt.ColList{sc[0], sc[1]},
		RightCols: opt.ColList{dup0, dup1},
		OutCols:   opt.ColList{out0, out1},
	})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nullable, nc := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(nullable, memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(nc[0], nc[1]),
	}))

	wide, wc := e.scan("nn3", 3)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wide, memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(wc[0], wc[1]),
	}))

	one, oc := e.scan("nn", 2)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(one, memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(oc[0]),
	}))
}
