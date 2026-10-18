package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateProjectConstantToDummyJoin(t *testing.T) {
	rule := loadRule(t, "AggregateProjectConstantToDummyJoin")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	k0 := e.f.Metadata().AddColumn("k0", types.Bool)
	k1 := e.f.Metadata().AddColumn("k1", types.Bool)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := func() memo.AggregationsExpr {
		return memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[0])), fcol),
		}
	}
	project := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), k0),
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), k1),
	}, opt.MakeColSet(sc[0]))
	before := e.f.ConstructGroupBy(project, aggs(), &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(k0, k1, sc[0]),
	})
	dummy := e.f.ConstructValues(memo.ScalarListExpr{
		e.f.ConstructTuple(
			memo.ScalarListExpr{e.f.ConstructTrue(), e.f.ConstructFalse()},
			types.MakeTuple([]*types.T{types.Bool, types.Bool}),
		),
	}, &memo.ValuesPrivate{Cols: opt.ColList{k0, k1}, ID: e.f.Metadata().NextUniqueID()})
	joined := e.f.ConstructInnerJoin(s, dummy, nil, &memo.JoinPrivate{})
	newProject := e.f.ConstructProject(joined, nil, opt.MakeColSet(k0, k1, sc[0]))
	want := e.f.ConstructGroupBy(newProject, aggs(), &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(k0, k1, sc[0]),
	})
	e.expectRewrite(rule, before, want)

	twoKeys := e.f.ConstructGroupBy(project, aggs(), &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(k0, sc[0]),
	})
	e.expectNoMatch(rule, twoKeys)

	trueSecond := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), k0),
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), k1),
	}, opt.MakeColSet(sc[0]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(trueSecond, aggs(), &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(k0, k1, sc[0]),
	}))

	wide, wc := e.scan("t", 2)
	wideProject := e.f.ConstructProject(wide, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), k0),
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), k1),
	}, opt.MakeColSet(wc[0]))
	wideAggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(wc[0])), fcol),
	}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wideProject, wideAggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(k0, k1, wc[0]),
	}))
}
