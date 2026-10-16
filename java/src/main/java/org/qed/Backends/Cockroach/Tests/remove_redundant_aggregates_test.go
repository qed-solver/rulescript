package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveRedundantAggregates(t *testing.T) {
	rule := loadRule(t, "RemoveRedundantAggregates")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	k1 := e.f.Metadata().AddColumn("k1", types.Int)
	k2 := e.f.Metadata().AddColumn("k2", types.Int)
	v := e.f.Metadata().AddColumn("v", types.Int)
	items := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), k1),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(2)), k2),
	}
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(sc[0])), v)}
	lower := e.f.ConstructGroupBy(e.f.ConstructProject(s, items, opt.MakeColSet(sc[0])), aggs,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})
	before := e.f.ConstructGroupBy(lower, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})
	want := e.f.ConstructGroupBy(e.f.ConstructProject(s, items[:1], opt.ColSet{}), nil,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, lower)
}
