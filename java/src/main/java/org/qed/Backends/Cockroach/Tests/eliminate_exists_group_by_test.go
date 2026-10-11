package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateExistsGroupBy(t *testing.T) {
	rule := loadRule(t, "EliminateExistsGroupBy")
	e := newEnv(t)
	outer, _ := e.scan("u", 2)
	input, cols := e.scan("t", 3)
	key := e.f.Metadata().AddColumn("key", types.Int)
	project := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), key),
	}, input.Relational().OutputCols)
	grouped := e.f.ConstructGroupBy(project, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(key),
	})
	before := e.f.ConstructSemiJoin(outer, grouped, nil, &memo.JoinPrivate{})
	want := e.f.ConstructSemiJoin(outer, input, nil, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(outer, grouped, nil, &memo.JoinPrivate{}))
	direct := e.f.ConstructGroupBy(input, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(outer, direct, nil, &memo.JoinPrivate{}))
}
