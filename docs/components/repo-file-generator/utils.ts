/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

/**
 * URLがhttps://で始まるかチェックする
 *
 * @param url - 検証するURL
 * @returns https://で始まる場合true
 */
export const isValidRepositoryUrl = (url: string): boolean => {
    return url.startsWith("https://");
};

/**
 * URLを検証し、結果とエラーメッセージを返す
 *
 * @param url - 検証するURL
 * @returns 検証結果とエラーメッセージ
 */
export const validateRepositoryUrl = (
    url: string,
): {
    isValid: boolean;
    error?: string;
} => {
    if (!isValidRepositoryUrl(url)) {
        return {
            isValid: false,
            error: "Please enter a valid URL starting with https://",
        };
    }
    return { isValid: true };
};
