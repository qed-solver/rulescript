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
	s, c := e.scan("t", 1)
	trueCol := e.f.Metadata().AddColumn("t", types.Bool)
	falseCol := e.f.Metadata().AddColumn("f", types.Bool)
	fcol := e.f.Metadata().AddColumn("f", types.Int)

	items := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), trueCol),
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), falseCol),
	}
	project := e.f.ConstructProject(s, items, opt.MakeColSet(c[0]))
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), fcol)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(trueCol, falseCol, c[0])}
	before := e.f.ConstructGroupBy(project, aggs, private)

	values := e.f.ConstructValues(memo.ScalarListExpr{
		e.f.ConstructTuple(
			memo.ScalarListExpr{e.f.ConstructTrue(), e.f.ConstructFalse()},
			types.MakeTuple([]*types.T{types.Bool, types.Bool}),
		),
	}, &memo.ValuesPrivate{
		Cols: opt.ColList{trueCol, falseCol},
		ID:   e.f.Metadata().NextUniqueID(),
	})
	joined := e.f.ConstructInnerJoin(s, values, nil, &memo.JoinPrivate{})
	newProject := e.f.ConstructProject(joined, nil, opt.MakeColSet(trueCol, falseCol, c[0]))
	want := e.f.ConstructGroupBy(newProject, aggs, private)
	e.expectRewrite(rule, before, want)

	swapped := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), trueCol),
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), falseCol),
	}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructProject(s, swapped, opt.MakeColSet(c[0])), aggs, private))

	distinctAggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(c[0]))), fcol),
	}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(project, distinctAggs, private))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(project, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(trueCol, c[0]),
	}))
}
