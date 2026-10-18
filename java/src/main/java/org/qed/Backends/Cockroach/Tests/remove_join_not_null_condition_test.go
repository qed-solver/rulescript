package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRemoveJoinNotNullCondition(t *testing.T) {
	rule := loadRule(t, "RemoveJoinNotNullCondition")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("t", 1)
	notNull := e.f.ConstructIsNot(e.v(leftCols[0]), memo.NullSingleton)
	rest := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
	before := e.f.ConstructLeftJoin(left, right, e.filters(notNull, rest), memo.EmptyJoinPrivate)
	want := e.f.ConstructLeftJoin(left, right, e.filters(rest), memo.EmptyJoinPrivate)
	e.expectRewrite(rule, before, want)

	inner := e.f.ConstructInnerJoin(left, right, e.filters(notNull, rest), &memo.JoinPrivate{})
	e.expectNoMatch(rule, inner)
}
