package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyEqualsAnyTuple(t *testing.T) {
	rule := loadRule(t, "SimplifyEqualsAnyTuple")
	e := newEnv(t)
	s, sc := e.scan("u", 1)
	tuple, tc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(tc[0])))
	before := e.f.ConstructSemiJoin(s, tuple, on, &memo.JoinPrivate{})
	member := e.f.ConstructAny(tuple, e.v(sc[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	e.expectRewrite(rule, before, e.f.ConstructSelect(s, e.filters(member)))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, tuple, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(s, tuple, on, &memo.JoinPrivate{}))
}
