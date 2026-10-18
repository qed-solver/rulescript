package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateAggDistinctForKeys(t *testing.T) {
	rule := loadRule(t, "EliminateAggDistinctForKeys")
	e := newEnv(t)
	out := e.f.Metadata().AddColumn("f", types.Int)
	agg := func(distinct bool, arg opt.ColumnID) opt.ScalarExpr {
		m := e.f.ConstructMax(e.v(arg))
		if distinct {
			return e.f.ConstructAggDistinct(m)
		}
		return m
	}
	grp := func(input memo.RelExpr, keys opt.ColSet, item opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructGroupBy(input, memo.AggregationsExpr{e.f.ConstructAggregationsItem(item, out)}, &memo.GroupingPrivate{GroupingCols: keys})
	}
	s, sc := e.scan("t", 1)
	before := grp(s, opt.MakeColSet(sc[0]), agg(true, sc[0]))
	after := grp(s, opt.MakeColSet(sc[0]), agg(false, sc[0]))
	e.expectRewrite(rule, before, after)
	e.expectNoMatch(rule, after)
	w, wc := e.scan("t", 2)
	e.expectNoMatch(rule, grp(w, opt.MakeColSet(wc[0]), agg(true, wc[1])))
	e.expectNoMatch(rule, grp(w, opt.MakeColSet(wc[0], wc[1]), agg(true, wc[0])))
}
