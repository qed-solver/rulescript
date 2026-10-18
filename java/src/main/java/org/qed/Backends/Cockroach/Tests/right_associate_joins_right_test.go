package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRightAssociateJoinsRight(t *testing.T) {
	rule := loadRule(t, "RightAssociateJoinsRight")
	e := newEnv(t)

	a, acols := e.scan("t", 1)
	b, bcols := e.scan("t", 1)
	c, ccols := e.scan("t", 1)
	ac := e.f.ConstructEq(e.v(acols[0]), e.v(ccols[0]))
	ab := e.f.ConstructEq(e.v(acols[0]), e.v(bcols[0]))
	bc := e.f.ConstructInnerJoin(b, c, nil, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(a, bc, e.filters(ac, ab), &memo.JoinPrivate{})

	acJoin := e.f.ConstructInnerJoin(a, c, e.filters(ac), &memo.JoinPrivate{})
	afterJoin := e.f.ConstructInnerJoin(acJoin, b, e.filters(ab), &memo.JoinPrivate{})
	want := e.f.ConstructProject(afterJoin, nil, opt.MakeColSet(acols[0], bcols[0], ccols[0]))
	e.expectRewrite(rule, before, want)

	e2 := newEnv(t)
	a2, a2cols := e2.scan("t", 1)
	b2, b2cols := e2.scan("t", 1)
	c2, c2cols := e2.scan("t", 1)
	innerCondition := e2.f.ConstructEq(e2.v(b2cols[0]), e2.v(c2cols[0]))
	outerCondition := e2.f.ConstructEq(e2.v(a2cols[0]), e2.v(c2cols[0]))
	nearMiss := e2.f.ConstructInnerJoin(a2,
		e2.f.ConstructInnerJoin(b2, c2, e2.filters(innerCondition), &memo.JoinPrivate{}),
		e2.filters(outerCondition), &memo.JoinPrivate{})
	e2.expectNoMatch(rule, nearMiss)
}
