package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateWindow(t *testing.T) {
	rule := loadRule(t, "EliminateWindow")
	e := newEnv(t)
	input, cols := e.scan("nn", 2)
	groupInput, groupCols := e.scan("nn", 2)
	group := e.f.ConstructGroupBy(groupInput, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(groupCols[0])})
	on := e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(groupCols[0])))
	join := e.f.ConstructInnerJoin(input, group, on, &memo.JoinPrivate{})
	joinCols := join.Relational().OutputCols.ToList()
	before := e.f.ConstructProject(join, nil, opt.MakeColSet(joinCols[0], joinCols[1]))
	e.expectRewrite(rule, before, input)
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(input, group, on, &memo.JoinPrivate{}), nil, opt.MakeColSet(joinCols[0], joinCols[1])))
}
