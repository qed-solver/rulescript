package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateWindow(t *testing.T) {
	rule := loadRule(t, "EliminateWindow")
	e := newEnv(t)
	input, ic := e.scan("nn", 2)
	partitions, pc := e.scan("nn", 2)
	keys := e.f.ConstructGroupBy(partitions, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(pc[0])})
	joined := func(on opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructProject(
			e.f.ConstructInnerJoin(input, keys, e.filters(on), &memo.JoinPrivate{}), nil, opt.MakeColSet(ic...))
	}
	e.expectRewrite(rule, joined(e.f.ConstructEq(e.v(ic[0]), e.v(pc[0]))), input)
	e.expectNoMatch(rule, joined(e.f.ConstructEq(e.v(ic[1]), e.v(pc[0]))))
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructLeftJoin(input, keys, e.filters(e.f.ConstructEq(e.v(ic[0]), e.v(pc[0]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(ic...)))
}
